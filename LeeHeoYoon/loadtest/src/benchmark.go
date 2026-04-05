//go:build !genfiles

package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"math"
	"math/rand"
	"mime/multipart"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"
)

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type FileCase struct {
	Name      string
	Size      int64
	Count     int
	Prefix    string
	Folders   []string
	PerFolder int
}

type UploadedFile struct {
	ID       int64
	Name     string
	FolderID int64
	Hash     string
	Size     int64
	Case     string
}

type LatencyStats struct {
	Min, Max, Avg, P50, P95, P99 float64
}

type CaseResult struct {
	CaseName    string
	Files       int
	Concurrency int
	TotalSec    float64
	Stats       LatencyStats
	MBps        float64
	OK          int
	Fail        int
	HashFail    int
	Verified    bool
	Latency     float64
}

// API response types — matches PR #6 server DTOs
type FileResp struct {
	ID           int64  `json:"id"`
	OriginalName string `json:"originalName"`
	FileSize     int64  `json:"fileSize"`
	ContentType  string `json:"contentType"`
	FolderID     *int64 `json:"folderId"`
	UploadStatus string `json:"uploadStatus"`
}

type FolderResp struct {
	ID       int64  `json:"id"`
	Name     string `json:"name"`
	ParentID *int64 `json:"parentId"`
}

type FolderContentsResp struct {
	FolderID   *int64       `json:"folderId"`
	FolderName string       `json:"folderName"`
	Folders    []FolderResp `json:"folders"`
	Files      []FileResp   `json:"files"`
}

type InitUploadResp struct {
	MetadataID  int64  `json:"metadataId"`
	UploadToken string `json:"uploadToken"`
}

// ---------------------------------------------------------------------------
// Global state
// ---------------------------------------------------------------------------

var (
	flagHost       string
	flagUserID     string
	flagUploadMode string
	flagDataDir    string
	flagSeed       int64
	flagTimeout    time.Duration

	httpClient *http.Client

	// folderIDs maps logical path (e.g. "bench/small/folderA") to server folder ID
	folderIDs   = make(map[string]int64)
	folderIDsMu sync.RWMutex

	fileCases = []FileCase{
		{Name: "small", Size: 3 << 20, Count: 1000, Prefix: "small",
			Folders: []string{"folderA", "folderB", "folderC", "folderD"}, PerFolder: 250},
		{Name: "medium", Size: 30 << 20, Count: 100, Prefix: "medium",
			Folders: []string{"folderA", "folderB"}, PerFolder: 50},
		{Name: "large", Size: 100 << 20, Count: 30, Prefix: "large",
			Folders: []string{"folderA", "folderB"}, PerFolder: 15},
		{Name: "xlarge", Size: 300 << 20, Count: 10, Prefix: "xlarge",
			Folders: []string{"folderA"}, PerFolder: 10},
		{Name: "huge", Size: 500 << 20, Count: 6, Prefix: "huge",
			Folders: []string{"folderA"}, PerFolder: 6},
	}
)

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

func main() {
	flag.StringVar(&flagHost, "host", "http://nginx:8080", "target server URL (through Nginx)")
	flag.StringVar(&flagUserID, "user-id", "1", "test userId (X-User-Id header)")
	flag.StringVar(&flagUploadMode, "upload-mode", "raw", "upload mode: multipart or raw (2-phase Nginx direct)")
	flag.StringVar(&flagDataDir, "data-dir", "./bench-data", "test files directory")
	flag.Int64Var(&flagSeed, "seed", 42, "random seed for file generation")
	timeout := flag.Duration("timeout", 5*time.Minute, "per-request timeout")
	flag.Parse()
	flagTimeout = *timeout

	httpClient = &http.Client{Timeout: flagTimeout}
	flagHost = strings.TrimRight(flagHost, "/")

	// Step 0: Generate local test files
	localFiles := generateTestFiles()

	// Step 1: Cleanup previous run
	fmt.Println()
	cleanupPreviousRun()

	// Step 2: Create folder tree on server
	fmt.Println()
	createFolderTree()

	// Step 3: Upload
	fmt.Println()
	uploadResults, uploadedFiles := scenarioUpload(localFiles)
	printUploadTable("Upload ("+flagUploadMode+")", uploadResults)

	// Step 4: Download
	fmt.Println()
	downloadResults := scenarioDownload(uploadedFiles)
	printDownloadTable("Download", downloadResults)

	// Step 5: Folder List
	fmt.Println()
	folderResults := scenarioFolderList(uploadedFiles)
	printFolderListTable("Folder List", folderResults)

	// Step 6: Move Folder
	fmt.Println()
	moveResults := scenarioMoveFolder()
	printMoveDeleteTable("Move Folder", moveResults)

	// Step 7: Delete Files
	fmt.Println()
	deleteFileResults := scenarioDeleteFiles()
	printMoveDeleteTable("Delete Files", deleteFileResults)

	// Step 8: Delete Folder
	fmt.Println()
	deleteFolderResults := scenarioDeleteFolder()
	printMoveDeleteTable("Delete Folder", deleteFolderResults)

	// Summary
	fmt.Println()
	printSummary(uploadResults, downloadResults, folderResults, moveResults, deleteFileResults, deleteFolderResults)
}

// ---------------------------------------------------------------------------
// HTTP helpers
// ---------------------------------------------------------------------------

func newRequest(method, url string, body io.Reader) *http.Request {
	req, err := http.NewRequest(method, url, body)
	if err != nil {
		fatalf("new request %s %s: %v", method, url, err)
	}
	req.Header.Set("X-User-Id", flagUserID)
	return req
}

func doJSON(req *http.Request, out interface{}) error {
	resp, err := httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("status %d: %s", resp.StatusCode, string(body))
	}

	if out != nil {
		if err := json.NewDecoder(resp.Body).Decode(out); err != nil {
			return fmt.Errorf("decode: %v", err)
		}
	}
	return nil
}

// ---------------------------------------------------------------------------
// Cleanup previous run
// ---------------------------------------------------------------------------

func cleanupPreviousRun() {
	fmt.Println("=== Cleanup previous run ===")

	url := fmt.Sprintf("%s/api/folders/root/contents?size=1000", flagHost)
	req := newRequest("GET", url, nil)

	var resp FolderContentsResp
	if err := doJSON(req, &resp); err != nil {
		fmt.Printf("  No previous data found (or error: %v)\n", err)
		return
	}

	for _, folder := range resp.Folders {
		if folder.Name == "bench" {
			fmt.Printf("  Found existing 'bench' folder (id=%d), deleting...\n", folder.ID)
			if err := doDeleteFolder(folder.ID); err != nil {
				fmt.Printf("  WARNING: failed to delete: %v\n", err)
			} else {
				fmt.Println("  Cleanup complete")
			}
			return
		}
	}
	fmt.Println("  No previous data found")
}

// ---------------------------------------------------------------------------
// Folder tree creation
// ---------------------------------------------------------------------------

func apiCreateFolder(name string, parentID *int64) int64 {
	body := map[string]interface{}{"name": name}
	if parentID != nil {
		body["parentId"] = *parentID
	}
	b, _ := json.Marshal(body)

	req := newRequest("POST", flagHost+"/api/folders", bytes.NewReader(b))
	req.Header.Set("Content-Type", "application/json")

	var resp FolderResp
	if err := doJSON(req, &resp); err != nil {
		fatalf("create folder %s: %v", name, err)
	}
	return resp.ID
}

func createFolderTree() {
	fmt.Println("=== Creating folder tree ===")

	// Root: bench
	benchID := apiCreateFolder("bench", nil)
	folderIDs["bench"] = benchID
	fmt.Printf("  bench (id=%d)\n", benchID)

	for _, fc := range fileCases {
		caseID := apiCreateFolder(fc.Prefix, &benchID)
		casePath := "bench/" + fc.Prefix
		folderIDs[casePath] = caseID

		for _, folder := range fc.Folders {
			fID := apiCreateFolder(folder, &caseID)
			fullPath := casePath + "/" + folder
			folderIDs[fullPath] = fID
		}
		fmt.Printf("  %s (id=%d) + %d sub-folders\n", casePath, caseID, len(fc.Folders))
	}

	// Move targets (one per move case to avoid name collisions)
	for _, mc := range moveCases() {
		tID := apiCreateFolder(mc.TargetName, &benchID)
		tPath := "bench/" + mc.TargetName
		folderIDs[tPath] = tID
	}
	fmt.Printf("  + %d move target folders\n", len(moveCases()))
	fmt.Printf("  Total: %d folders created\n", len(folderIDs))
}

// ---------------------------------------------------------------------------
// Test file generation
// ---------------------------------------------------------------------------

type localFile struct {
	Path   string
	Hash   string
	Size   int64
	Case   string
	Folder string // logical path: "bench/small/folderA"
}

func generateTestFiles() []localFile {
	fmt.Println("=== Loading test files ===")
	var files []localFile

	for _, fc := range fileCases {
		caseDir := filepath.Join(flagDataDir, fc.Prefix)
		idx := 0
		reused, created := 0, 0
		for _, folder := range fc.Folders {
			folderDir := filepath.Join(caseDir, folder)
			folderPath := "bench/" + fc.Prefix + "/" + folder
			for i := 0; i < fc.PerFolder; i++ {
				idx++
				fname := fmt.Sprintf("%s_%04d.bin", fc.Prefix, idx)
				fpath := filepath.Join(folderDir, fname)

				if info, err := os.Stat(fpath); err == nil && info.Size() == fc.Size {
					h, err := hashFile(fpath)
					if err != nil {
						fatalf("hash %s: %v", fpath, err)
					}
					files = append(files, localFile{Path: fpath, Hash: h, Size: fc.Size, Case: fc.Name, Folder: folderPath})
					reused++
					continue
				}

				if err := os.MkdirAll(folderDir, 0o755); err != nil {
					fatalf("mkdir %s: %v", folderDir, err)
				}
				h, err := generateFile(fpath, fc.Size, flagSeed+int64(idx))
				if err != nil {
					fatalf("generate %s: %v", fpath, err)
				}
				files = append(files, localFile{Path: fpath, Hash: h, Size: fc.Size, Case: fc.Name, Folder: folderPath})
				created++
				if created%100 == 0 {
					fmt.Printf("  [%s] creating... %d files\n", fc.Name, created)
				}
			}
		}
		if created > 0 {
			fmt.Printf("  [%s] created %d, reused %d\n", fc.Name, created, reused)
		} else {
			fmt.Printf("  [%s] reused %d files\n", fc.Name, reused)
		}
	}
	fmt.Printf("  Total: %d files loaded\n", len(files))
	return files
}

func generateFile(path string, size int64, seed int64) (string, error) {
	f, err := os.Create(path)
	if err != nil {
		return "", err
	}
	defer f.Close()

	rng := rand.New(rand.NewSource(seed))
	hasher := sha256.New()
	w := io.MultiWriter(f, hasher)
	buf := make([]byte, 64*1024)
	remaining := size
	for remaining > 0 {
		n := int64(len(buf))
		if n > remaining {
			n = remaining
		}
		rng.Read(buf[:n])
		if _, err := w.Write(buf[:n]); err != nil {
			return "", err
		}
		remaining -= n
	}
	return hex.EncodeToString(hasher.Sum(nil)), nil
}

func hashFile(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()
	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

// ---------------------------------------------------------------------------
// Scenario 1: Upload
// ---------------------------------------------------------------------------

func scenarioUpload(files []localFile) ([]CaseResult, []UploadedFile) {
	fmt.Println("=== Scenario: Upload ===")

	var allUploaded []UploadedFile
	var allUploadedMu sync.Mutex
	var results []CaseResult

	for _, fc := range fileCases {
		var caseFiles []localFile
		for _, f := range files {
			if f.Case == fc.Name {
				caseFiles = append(caseFiles, f)
			}
		}

		fmt.Printf("  [%s] uploading %d files (concurrency=%d)...\n", fc.Name, len(caseFiles), len(caseFiles))

		type uploadResult struct {
			file    localFile
			id      int64
			latency time.Duration
			err     error
		}

		resultsCh := make(chan uploadResult, len(caseFiles))
		var wg sync.WaitGroup

		start := time.Now()
		for _, lf := range caseFiles {
			wg.Add(1)
			go func(lf localFile) {
				defer wg.Done()
				folderIDsMu.RLock()
				fID := folderIDs[lf.Folder]
				folderIDsMu.RUnlock()

				t := time.Now()
				id, err := doUpload(lf, fID)
				resultsCh <- uploadResult{file: lf, id: id, latency: time.Since(t), err: err}
			}(lf)
		}
		wg.Wait()
		close(resultsCh)
		totalDur := time.Since(start)

		var latencies []float64
		ok, fail := 0, 0
		for r := range resultsCh {
			latencies = append(latencies, float64(r.latency.Milliseconds()))
			if r.err != nil {
				fail++
				if fail <= 5 {
					fmt.Printf("    FAIL %s: %v\n", filepath.Base(r.file.Path), r.err)
				}
			} else {
				ok++
				allUploadedMu.Lock()
				allUploaded = append(allUploaded, UploadedFile{
					ID: r.id, Name: filepath.Base(r.file.Path),
					FolderID: folderIDs[r.file.Folder],
					Hash: r.file.Hash, Size: r.file.Size, Case: r.file.Case,
				})
				allUploadedMu.Unlock()
			}
		}
		if fail > 5 {
			fmt.Printf("    ... and %d more failures\n", fail-5)
		}

		totalMB := float64(fc.Size) * float64(ok) / (1024 * 1024)
		results = append(results, CaseResult{
			CaseName: fc.Name, Files: len(caseFiles), Concurrency: len(caseFiles),
			TotalSec: totalDur.Seconds(), Stats: calcStats(latencies),
			MBps: totalMB / totalDur.Seconds(), OK: ok, Fail: fail,
		})
	}

	return results, allUploaded
}

func doUpload(lf localFile, folderID int64) (int64, error) {
	if flagUploadMode == "raw" {
		return doUploadRaw(lf, folderID)
	}
	return doUploadMultipart(lf, folderID)
}

func doUploadMultipart(lf localFile, folderID int64) (int64, error) {
	f, err := os.Open(lf.Path)
	if err != nil {
		return 0, err
	}
	defer f.Close()

	pr, pw := io.Pipe()
	writer := multipart.NewWriter(pw)

	go func() {
		defer pw.Close()
		defer writer.Close()
		_ = writer.WriteField("folderId", fmt.Sprintf("%d", folderID))
		part, err := writer.CreateFormFile("file", filepath.Base(lf.Path))
		if err != nil {
			pw.CloseWithError(err)
			return
		}
		if _, err := io.Copy(part, f); err != nil {
			pw.CloseWithError(err)
		}
	}()

	req := newRequest("POST", flagHost+"/api/files", pr)
	req.Header.Set("Content-Type", writer.FormDataContentType())

	var resp FileResp
	if err := doJSON(req, &resp); err != nil {
		return 0, err
	}
	return resp.ID, nil
}

func doUploadRaw(lf localFile, folderID int64) (int64, error) {
	// Phase 1: Init upload → get metadataId + uploadToken
	initBody, _ := json.Marshal(map[string]interface{}{
		"fileName":    filepath.Base(lf.Path),
		"fileSize":    lf.Size,
		"contentType": "application/octet-stream",
		"folderId":    folderID,
	})
	initReq := newRequest("POST", flagHost+"/api/files/init", bytes.NewReader(initBody))
	initReq.Header.Set("Content-Type", "application/json")

	var initResp InitUploadResp
	if err := doJSON(initReq, &initResp); err != nil {
		return 0, fmt.Errorf("init: %v", err)
	}

	// Phase 2: Upload raw binary to Nginx direct-to-disk
	f, err := os.Open(lf.Path)
	if err != nil {
		return 0, err
	}
	defer f.Close()

	uploadURL := fmt.Sprintf("%s/upload/%d?token=%s", flagHost, initResp.MetadataID, initResp.UploadToken)
	uploadReq := newRequest("POST", uploadURL, f)
	uploadReq.Header.Set("Content-Type", "application/octet-stream")
	uploadReq.ContentLength = lf.Size

	var fileResp FileResp
	if err := doJSON(uploadReq, &fileResp); err != nil {
		return 0, fmt.Errorf("upload: %v", err)
	}
	return fileResp.ID, nil
}

// ---------------------------------------------------------------------------
// Scenario 2: Download
// ---------------------------------------------------------------------------

func scenarioDownload(files []UploadedFile) []CaseResult {
	fmt.Println("=== Scenario: Download ===")
	var results []CaseResult

	for _, fc := range fileCases {
		var caseFiles []UploadedFile
		for _, f := range files {
			if f.Case == fc.Name {
				caseFiles = append(caseFiles, f)
			}
		}
		if len(caseFiles) == 0 {
			continue
		}

		fmt.Printf("  [%s] downloading %d files (concurrency=%d)...\n", fc.Name, len(caseFiles), len(caseFiles))

		type dlResult struct {
			latency  time.Duration
			err      error
			hashFail bool
		}

		resultsCh := make(chan dlResult, len(caseFiles))
		var wg sync.WaitGroup

		start := time.Now()
		for _, uf := range caseFiles {
			wg.Add(1)
			go func(uf UploadedFile) {
				defer wg.Done()
				t := time.Now()
				hashFail, err := doDownloadAndVerify(uf)
				resultsCh <- dlResult{latency: time.Since(t), err: err, hashFail: hashFail}
			}(uf)
		}
		wg.Wait()
		close(resultsCh)
		totalDur := time.Since(start)

		var latencies []float64
		ok, fail, hashFail := 0, 0, 0
		for r := range resultsCh {
			latencies = append(latencies, float64(r.latency.Milliseconds()))
			if r.err != nil {
				fail++
			} else if r.hashFail {
				hashFail++
			} else {
				ok++
			}
		}

		totalMB := float64(fc.Size) * float64(ok+hashFail) / (1024 * 1024)
		results = append(results, CaseResult{
			CaseName: fc.Name, Files: len(caseFiles), Concurrency: len(caseFiles),
			TotalSec: totalDur.Seconds(), Stats: calcStats(latencies),
			MBps: totalMB / totalDur.Seconds(), OK: ok, Fail: fail, HashFail: hashFail,
		})
	}
	return results
}

func doDownloadAndVerify(uf UploadedFile) (hashFail bool, err error) {
	// GET /api/files/{id} — Nginx intercepts X-Accel-Redirect and serves the file
	req := newRequest("GET", fmt.Sprintf("%s/api/files/%d", flagHost, uf.ID), nil)

	resp, err := httpClient.Do(req)
	if err != nil {
		return false, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return false, fmt.Errorf("status %d: %s", resp.StatusCode, string(body))
	}

	hasher := sha256.New()
	n, err := io.Copy(hasher, resp.Body)
	if err != nil {
		return false, fmt.Errorf("read body: %v", err)
	}
	if n != uf.Size {
		return true, nil
	}
	got := hex.EncodeToString(hasher.Sum(nil))
	if got != uf.Hash {
		return true, nil
	}
	return false, nil
}

// ---------------------------------------------------------------------------
// Scenario 3: Folder List
// ---------------------------------------------------------------------------

func scenarioFolderList(files []UploadedFile) []CaseResult {
	fmt.Println("=== Scenario: Folder List ===")

	// Build expected file count per folder ID
	expectedFiles := make(map[int64]int)
	for _, f := range files {
		expectedFiles[f.FolderID]++
	}

	// Collect leaf folders that have uploaded files
	var paths []string
	for path := range folderIDs {
		if expectedFiles[folderIDs[path]] > 0 {
			paths = append(paths, path)
		}
	}
	sort.Strings(paths)

	var allLatencies []float64
	ok, fail, verifyFail := 0, 0, 0

	start := time.Now()
	for _, path := range paths {
		fID := folderIDs[path]
		expected := expectedFiles[fID]

		t := time.Now()
		contents, err := doFolderContents(fID)
		lat := time.Since(t)
		allLatencies = append(allLatencies, float64(lat.Milliseconds()))

		if err != nil {
			fail++
			fmt.Printf("    FAIL %s: %v\n", path, err)
		} else if len(contents.Files) != expected {
			verifyFail++
			fmt.Printf("    VERIFY FAIL %s: expected %d, got %d\n", path, expected, len(contents.Files))
		} else {
			ok++
		}
	}
	totalDur := time.Since(start)

	return []CaseResult{{
		CaseName: "all folders", Files: len(paths), Concurrency: 1,
		TotalSec: totalDur.Seconds(), Stats: calcStats(allLatencies),
		OK: ok, Fail: fail, HashFail: verifyFail,
		Verified: verifyFail == 0 && fail == 0,
	}}
}

func doFolderContents(folderID int64) (*FolderContentsResp, error) {
	url := fmt.Sprintf("%s/api/folders/%d/contents?size=1000", flagHost, folderID)
	req := newRequest("GET", url, nil)
	var resp FolderContentsResp
	if err := doJSON(req, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

// ---------------------------------------------------------------------------
// Scenario 4: Move Folder
// ---------------------------------------------------------------------------

type moveCase struct {
	Name       string
	SourcePath string // e.g. "bench/small/folderA"
	TargetName string // target folder name under bench/
	Files      int
}

func moveCases() []moveCase {
	return []moveCase{
		{"small/folderA", "bench/small/folderA", "moved_s_a", 250},
		{"small/folderB", "bench/small/folderB", "moved_s_b", 250},
		{"medium/folderA", "bench/medium/folderA", "moved_m_a", 50},
		{"large/folderA", "bench/large/folderA", "moved_l_a", 15},
		{"xlarge/folderA", "bench/xlarge/folderA", "moved_xl", 10},
	}
}

func scenarioMoveFolder() []CaseResult {
	fmt.Println("=== Scenario: Move Folder ===")

	var results []CaseResult
	for _, mc := range moveCases() {
		sourceID := folderIDs[mc.SourcePath]
		targetID := folderIDs["bench/"+mc.TargetName]

		fmt.Printf("  [%s] moving to %s (%d files)...\n", mc.Name, mc.TargetName, mc.Files)

		t := time.Now()
		err := doMoveFolder(sourceID, targetID)
		lat := time.Since(t)

		verified := false
		if err != nil {
			fmt.Printf("    FAIL: %v\n", err)
		} else {
			// Verify: target should contain the moved folder as a child
			contents, err := doFolderContents(targetID)
			if err != nil {
				fmt.Printf("    VERIFY FAIL: %v\n", err)
			} else if len(contents.Folders) == 0 {
				fmt.Printf("    VERIFY FAIL: target has no sub-folders\n")
			} else {
				// Check moved folder's file count
				movedID := contents.Folders[0].ID
				inner, err := doFolderContents(movedID)
				if err != nil {
					fmt.Printf("    VERIFY FAIL (inner): %v\n", err)
				} else if len(inner.Files) != mc.Files {
					fmt.Printf("    VERIFY FAIL: expected %d files, got %d\n", mc.Files, len(inner.Files))
				} else {
					verified = true
				}
			}
		}

		results = append(results, CaseResult{
			CaseName: mc.Name, Files: mc.Files,
			Latency: float64(lat.Milliseconds()), Verified: verified,
			OK: boolToInt(err == nil), Fail: boolToInt(err != nil),
		})
	}
	return results
}

func doMoveFolder(folderID, targetFolderID int64) error {
	body, _ := json.Marshal(map[string]interface{}{"targetFolderId": targetFolderID})
	req := newRequest("PATCH", fmt.Sprintf("%s/api/folders/%d/move", flagHost, folderID), bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	return doJSON(req, nil)
}

// ---------------------------------------------------------------------------
// Scenario 5: Delete Files
// ---------------------------------------------------------------------------

func scenarioDeleteFiles() []CaseResult {
	fmt.Println("=== Scenario: Delete Files ===")

	cases := []struct {
		Name       string
		FolderPath string
	}{
		{"small/folderC", "bench/small/folderC"},
		{"small/folderD", "bench/small/folderD"},
		{"medium/folderB", "bench/medium/folderB"},
	}

	var results []CaseResult
	for _, dc := range cases {
		folderID := folderIDs[dc.FolderPath]
		fmt.Printf("  [%s] listing files...\n", dc.Name)

		contents, err := doFolderContents(folderID)
		if err != nil {
			fmt.Printf("    FAIL list: %v\n", err)
			results = append(results, CaseResult{CaseName: dc.Name, Fail: 1})
			continue
		}

		fileIDs := make([]int64, len(contents.Files))
		for i, f := range contents.Files {
			fileIDs[i] = f.ID
		}

		fmt.Printf("  [%s] deleting %d files (concurrency=%d)...\n", dc.Name, len(fileIDs), len(fileIDs))

		type delResult struct {
			latency time.Duration
			err     error
		}
		resultsCh := make(chan delResult, len(fileIDs))
		var wg sync.WaitGroup

		start := time.Now()
		for _, fID := range fileIDs {
			wg.Add(1)
			go func(fID int64) {
				defer wg.Done()
				t := time.Now()
				err := doDeleteFile(fID)
				resultsCh <- delResult{latency: time.Since(t), err: err}
			}(fID)
		}
		wg.Wait()
		close(resultsCh)
		totalDur := time.Since(start)

		var latencies []float64
		ok, fail := 0, 0
		for r := range resultsCh {
			latencies = append(latencies, float64(r.latency.Milliseconds()))
			if r.err != nil {
				fail++
			} else {
				ok++
			}
		}

		// Verify: folder should be empty
		verified := false
		after, err := doFolderContents(folderID)
		if err != nil {
			fmt.Printf("    VERIFY FAIL: %v\n", err)
		} else if len(after.Files) == 0 {
			verified = true
		} else {
			fmt.Printf("    VERIFY FAIL: still has %d files\n", len(after.Files))
		}

		results = append(results, CaseResult{
			CaseName: dc.Name, Files: len(fileIDs), Concurrency: len(fileIDs),
			TotalSec: totalDur.Seconds(), Stats: calcStats(latencies),
			OK: ok, Fail: fail, Verified: verified,
		})
	}
	return results
}

func doDeleteFile(fileID int64) error {
	req := newRequest("DELETE", fmt.Sprintf("%s/api/files/%d", flagHost, fileID), nil)
	return doJSON(req, nil)
}

// ---------------------------------------------------------------------------
// Scenario 6: Delete Folder
// ---------------------------------------------------------------------------

func scenarioDeleteFolder() []CaseResult {
	fmt.Println("=== Scenario: Delete Folder ===")

	cases := []struct {
		Name       string
		FolderPath string
		Files      int
	}{
		{"moved_s_a (small)", "bench/moved_s_a", 250},
		{"moved_s_b (small)", "bench/moved_s_b", 250},
		{"moved_m_a (medium)", "bench/moved_m_a", 50},
		{"moved_l_a (large)", "bench/moved_l_a", 15},
		{"moved_xl (xlarge)", "bench/moved_xl", 10},
		{"large/folderB", "bench/large/folderB", 15},
		{"huge/folderA", "bench/huge/folderA", 6},
	}

	var results []CaseResult
	for _, dc := range cases {
		folderID := folderIDs[dc.FolderPath]
		fmt.Printf("  [%s] deleting folder %d (~%d files)...\n", dc.Name, folderID, dc.Files)

		t := time.Now()
		err := doDeleteFolder(folderID)
		lat := time.Since(t)

		verified := false
		if err != nil {
			fmt.Printf("    FAIL: %v\n", err)
		} else {
			// Verify: folder should be gone (404 or empty)
			contents, err := doFolderContents(folderID)
			if err != nil {
				verified = true // 404 = correctly deleted
			} else if len(contents.Files) == 0 && len(contents.Folders) == 0 {
				verified = true
			} else {
				fmt.Printf("    VERIFY FAIL: still has %d files, %d folders\n",
					len(contents.Files), len(contents.Folders))
			}
		}

		results = append(results, CaseResult{
			CaseName: dc.Name, Files: dc.Files,
			Latency: float64(lat.Milliseconds()), Verified: verified,
			OK: boolToInt(err == nil), Fail: boolToInt(err != nil),
		})
	}
	return results
}

func doDeleteFolder(folderID int64) error {
	req := newRequest("DELETE", fmt.Sprintf("%s/api/folders/%d", flagHost, folderID), nil)
	return doJSON(req, nil)
}

// ---------------------------------------------------------------------------
// Statistics
// ---------------------------------------------------------------------------

func calcStats(latencies []float64) LatencyStats {
	if len(latencies) == 0 {
		return LatencyStats{}
	}
	sort.Float64s(latencies)
	sum := 0.0
	for _, v := range latencies {
		sum += v
	}
	return LatencyStats{
		Min: latencies[0], Max: latencies[len(latencies)-1],
		Avg: sum / float64(len(latencies)),
		P50: percentile(latencies, 50), P95: percentile(latencies, 95), P99: percentile(latencies, 99),
	}
}

func percentile(sorted []float64, p float64) float64 {
	if len(sorted) == 0 {
		return 0
	}
	idx := p / 100.0 * float64(len(sorted)-1)
	lower := int(math.Floor(idx))
	upper := int(math.Ceil(idx))
	if lower == upper {
		return sorted[lower]
	}
	frac := idx - float64(lower)
	return sorted[lower]*(1-frac) + sorted[upper]*frac
}

// ---------------------------------------------------------------------------
// Output formatting
// ---------------------------------------------------------------------------

const (
	lineW = 120
	sep   = "\u2550"
	thin  = "\u2500"
)

func hline(char string) string {
	return strings.Repeat(char, lineW)
}

func printUploadTable(title string, results []CaseResult) {
	fmt.Println(hline(sep))
	fmt.Printf("  SCENARIO: %s\n", title)
	fmt.Println(hline(sep))
	fmt.Printf("  %-10s %5s %5s %9s %8s %8s %8s %8s %8s %8s %8s %5s %5s\n",
		"Case", "Files", "Conc", "Total(s)", "Avg(ms)", "P50(ms)", "P95(ms)", "P99(ms)", "Min(ms)", "Max(ms)", "MB/s", "OK", "Fail")
	fmt.Printf("  %s\n", hline(thin))
	for _, r := range results {
		fmt.Printf("  %-10s %5d %5d %9.2f %8.1f %8.1f %8.1f %8.1f %8.1f %8.1f %8.1f %5d %5d\n",
			r.CaseName, r.Files, r.Concurrency, r.TotalSec,
			r.Stats.Avg, r.Stats.P50, r.Stats.P95, r.Stats.P99,
			r.Stats.Min, r.Stats.Max, r.MBps, r.OK, r.Fail)
	}
	fmt.Println(hline(sep))
}

func printDownloadTable(title string, results []CaseResult) {
	fmt.Println(hline(sep))
	fmt.Printf("  SCENARIO: %s\n", title)
	fmt.Println(hline(sep))
	fmt.Printf("  %-10s %5s %5s %9s %8s %8s %8s %8s %8s %8s %8s %5s %5s %5s\n",
		"Case", "Files", "Conc", "Total(s)", "Avg(ms)", "P50(ms)", "P95(ms)", "P99(ms)", "Min(ms)", "Max(ms)", "MB/s", "OK", "Fail", "Hash\u2717")
	fmt.Printf("  %s\n", hline(thin))
	for _, r := range results {
		fmt.Printf("  %-10s %5d %5d %9.2f %8.1f %8.1f %8.1f %8.1f %8.1f %8.1f %8.1f %5d %5d %5d\n",
			r.CaseName, r.Files, r.Concurrency, r.TotalSec,
			r.Stats.Avg, r.Stats.P50, r.Stats.P95, r.Stats.P99,
			r.Stats.Min, r.Stats.Max, r.MBps, r.OK, r.Fail, r.HashFail)
	}
	fmt.Println(hline(sep))
}

func printFolderListTable(title string, results []CaseResult) {
	fmt.Println(hline(sep))
	fmt.Printf("  SCENARIO: %s\n", title)
	fmt.Println(hline(sep))
	fmt.Printf("  %-15s %5s %9s %8s %8s %8s %8s %8s %8s %5s %5s %8s\n",
		"Case", "Reqs", "Total(s)", "Avg(ms)", "P50(ms)", "P95(ms)", "P99(ms)", "Min(ms)", "Max(ms)", "OK", "Fail", "Verified")
	fmt.Printf("  %s\n", hline(thin))
	for _, r := range results {
		v := "\u2717"
		if r.Verified {
			v = "\u2713"
		}
		fmt.Printf("  %-15s %5d %9.2f %8.1f %8.1f %8.1f %8.1f %8.1f %8.1f %5d %5d %8s\n",
			r.CaseName, r.Files, r.TotalSec,
			r.Stats.Avg, r.Stats.P50, r.Stats.P95, r.Stats.P99,
			r.Stats.Min, r.Stats.Max, r.OK, r.Fail, v)
	}
	fmt.Println(hline(sep))
}

func printMoveDeleteTable(title string, results []CaseResult) {
	fmt.Println(hline(sep))
	fmt.Printf("  SCENARIO: %s\n", title)
	fmt.Println(hline(sep))

	hasStats := false
	for _, r := range results {
		if r.TotalSec > 0 {
			hasStats = true
			break
		}
	}

	if hasStats {
		fmt.Printf("  %-22s %5s %5s %9s %8s %8s %8s %8s %5s %5s %8s\n",
			"Case", "Files", "Conc", "Total(s)", "Avg(ms)", "P50(ms)", "P95(ms)", "P99(ms)", "OK", "Fail", "Verified")
		fmt.Printf("  %s\n", hline(thin))
		for _, r := range results {
			v := "\u2717"
			if r.Verified {
				v = "\u2713"
			}
			fmt.Printf("  %-22s %5d %5d %9.2f %8.1f %8.1f %8.1f %8.1f %5d %5d %8s\n",
				r.CaseName, r.Files, r.Concurrency, r.TotalSec,
				r.Stats.Avg, r.Stats.P50, r.Stats.P95, r.Stats.P99,
				r.OK, r.Fail, v)
		}
	} else {
		fmt.Printf("  %-28s %5s %12s %8s\n", "Case", "Files", "Latency(ms)", "Verified")
		fmt.Printf("  %s\n", hline(thin))
		for _, r := range results {
			v := "\u2717"
			if r.Verified {
				v = "\u2713"
			}
			fmt.Printf("  %-28s %5d %12.1f %8s\n", r.CaseName, r.Files, r.Latency, v)
		}
	}
	fmt.Println(hline(sep))
}

func printSummary(upload, download, folderList, move, deleteFile, deleteFolder []CaseResult) {
	fmt.Println(hline(sep))
	fmt.Println("  SUMMARY")
	fmt.Println(hline(sep))
	fmt.Printf("  %-15s %12s %12s %12s %15s\n",
		"Scenario", "Total(s)", "Total Files", "Success %", "Avg MB/s")
	fmt.Printf("  %s\n", hline(thin))

	scenarios := []struct {
		name    string
		results []CaseResult
	}{
		{"Upload", upload}, {"Download", download}, {"Folder List", folderList},
		{"Move Folder", move}, {"Delete Files", deleteFile}, {"Delete Folder", deleteFolder},
	}

	for _, s := range scenarios {
		totalTime, totalFiles, totalOK, totalFail := 0.0, 0, 0, 0
		totalMBps := 0.0
		mbpsCount := 0

		for _, r := range s.results {
			if r.TotalSec > 0 {
				totalTime += r.TotalSec
			} else {
				totalTime += r.Latency / 1000.0
			}
			totalFiles += r.Files
			totalOK += r.OK
			totalFail += r.Fail
			if r.MBps > 0 {
				totalMBps += r.MBps
				mbpsCount++
			}
		}

		rate := 0.0
		if totalOK+totalFail > 0 {
			rate = float64(totalOK) / float64(totalOK+totalFail) * 100
		}
		avg := "\u2014"
		if mbpsCount > 0 {
			avg = fmt.Sprintf("%.1f", totalMBps/float64(mbpsCount))
		}

		fmt.Printf("  %-15s %12.2f %12d %11.1f%% %15s\n",
			s.name, totalTime, totalFiles, rate, avg)
	}
	fmt.Println(hline(sep))
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

func boolToInt(b bool) int {
	if b {
		return 1
	}
	return 0
}

func fatalf(format string, args ...interface{}) {
	fmt.Fprintf(os.Stderr, "FATAL: "+format+"\n", args...)
	os.Exit(1)
}

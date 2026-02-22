package com.mystorage.dto.response;

import java.util.List;

public record FolderContentsResponse(
    Long folderId,
    String folderName,
    List<FolderResponse> folders,
    List<FileResponse> files
) {}

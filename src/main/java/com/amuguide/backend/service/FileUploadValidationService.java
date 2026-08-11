package com.amuguide.backend.service;

import com.amuguide.backend.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

@Service
public class FileUploadValidationService {

    private static final long MAX_IMPORT_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    public void validate(MultipartFile file, String expectedExtension, Set<String> allowedContentTypes) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Le fichier est obligatoire");
        }
        if (file.getSize() > MAX_IMPORT_FILE_SIZE_BYTES) {
            throw new BadRequestException("Le fichier ne doit pas depasser 10 Mo");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new BadRequestException("Le nom du fichier est obligatoire");
        }
        String lowerFilename = filename.toLowerCase(Locale.ROOT);
        if (!lowerFilename.endsWith("." + expectedExtension.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("Extension de fichier invalide. Extension attendue : ." + expectedExtension);
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !allowedContentTypes.contains(contentType)) {
            throw new BadRequestException("Type de fichier invalide : " + contentType);
        }
    }
}

package com.example.docbot.controller;

import com.example.docbot.dto.DeleteDocumentResponseDto;
import com.example.docbot.dto.DocumentSummaryDto;
import com.example.docbot.dto.UploadResponseDto;
import com.example.docbot.service.DocumentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/documents")
@CrossOrigin
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public UploadResponseDto upload(@RequestParam("file") MultipartFile file) {
        return documentService.uploadPdf(file);
    }

    @GetMapping
    public List<DocumentSummaryDto> listDocuments() {
        return documentService.listDocuments();
    }

    @DeleteMapping("/{filename}")
    public DeleteDocumentResponseDto deleteDocument(@PathVariable String filename) {
        return documentService.deleteDocument(filename);
    }
}

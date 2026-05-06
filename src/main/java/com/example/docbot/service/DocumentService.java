package com.example.docbot.service;

import com.example.docbot.config.RagProperties;
import com.example.docbot.dto.DeleteDocumentResponseDto;
import com.example.docbot.dto.DocumentSummaryDto;
import com.example.docbot.dto.UploadResponseDto;
import com.example.docbot.security.AuthUser;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocumentService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final RagProperties ragProperties;
    private final CurrentUserService currentUserService;

    public DocumentService(
            VectorStore vectorStore,
            JdbcTemplate jdbcTemplate,
            RagProperties ragProperties,
            CurrentUserService currentUserService
    ) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.ragProperties = ragProperties;
        this.currentUserService = currentUserService;
    }

    public UploadResponseDto uploadPdf(MultipartFile file) {

        try {
            AuthUser currentUser = currentUserService.requireUser();
            String filename = file.getOriginalFilename() == null
                    ? "unknown.pdf"
                    : file.getOriginalFilename();

            byte[] pdfBytes = file.getBytes();

            ApachePdfBoxDocumentParser parser =
                    new ApachePdfBoxDocumentParser();

            Document parsedDoc = parser.parse(
                    new ByteArrayInputStream(pdfBytes)
            );

            List<org.springframework.ai.document.Document> chunks =
                    splitIntoChunks(parsedDoc.text(), filename, currentUser.userId());

            if (chunks.isEmpty()) {
                return new UploadResponseDto(filename, "PDF has no readable text", 0);
            }

            vectorStore.add(chunks);

            return new UploadResponseDto(filename, "PDF embedded successfully", chunks.size());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<DocumentSummaryDto> listDocuments() {
        AuthUser currentUser = currentUserService.requireUser();

        return jdbcTemplate.query("""
                        SELECT metadata->>'filename' AS filename, COUNT(*) AS chunks
                        FROM vector_store
                        WHERE metadata->>'userId' = ?
                        GROUP BY metadata->>'filename'
                        ORDER BY metadata->>'filename'
                        """,
                preparedStatement -> preparedStatement.setString(1, currentUser.userId()),
                (rs, rowNum) -> new DocumentSummaryDto(
                        rs.getString("filename"),
                        rs.getInt("chunks")
                )
        );
    }

    public DeleteDocumentResponseDto deleteDocument(String filename) {
        AuthUser currentUser = currentUserService.requireUser();
        int deletedRows = jdbcTemplate.update(
                "DELETE FROM vector_store WHERE metadata->>'filename' = ? AND metadata->>'userId' = ?",
                filename,
                currentUser.userId()
        );

        String status = deletedRows == 0
                ? "No indexed chunks found for document"
                : "Document removed from index";

        return new DeleteDocumentResponseDto(filename, deletedRows, status);
    }

    private List<org.springframework.ai.document.Document> splitIntoChunks(
            String text,
            String filename,
            String userId
    ) {
        String normalizedText = text == null
                ? ""
                : text.replaceAll("\\s+", " ").trim();

        List<org.springframework.ai.document.Document> chunks = new ArrayList<>();

        if (normalizedText.isBlank()) {
            return chunks;
        }

        int start = 0;
        int chunkIndex = 0;
        int chunkSize = ragProperties.getIngestion().getChunkSize();
        int chunkOverlap = ragProperties.getIngestion().getChunkOverlap();

        while (start < normalizedText.length()) {
            int end = Math.min(start + chunkSize, normalizedText.length());
            String chunkText = normalizedText.substring(start, end).trim();

            if (!chunkText.isBlank()) {
                chunks.add(new org.springframework.ai.document.Document(
                        chunkText,
                        Map.of(
                                "filename", filename == null ? "unknown.pdf" : filename,
                                "chunkIndex", chunkIndex,
                                "userId", userId
                        )
                ));
            }

            if (end == normalizedText.length()) {
                break;
            }

            start = Math.max(end - chunkOverlap, start + 1);
            chunkIndex++;
        }

        return chunks;
    }
}

// src/main/java/legacymodernizer/parser/controller/FileUploadController.java
package legacymodernizer.parser.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import legacymodernizer.parser.service.AnalysisProcessingService;

import java.util.HashMap;
import java.util.Map;

@RestController
public class FileUploadController {

    private final AnalysisProcessingService analysisProcessingService;

    public FileUploadController(AnalysisProcessingService analysisProcessingService) {
        this.analysisProcessingService = analysisProcessingService;
    }

    @PostMapping("/uploadFile")
    public ResponseEntity<Map<String, String>> handleFileUpload(@RequestParam("file") MultipartFile file) {
        try {
            return analysisProcessingService.analsisFile(file);
        } catch (Exception e) {
            Map<String, String> errorDetails = new HashMap<>();
            errorDetails.put("error", "Failed to Analysis Content" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDetails);
        }
    }
}
package legacymodernizer.parser.controller;

import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import legacymodernizer.parser.service.PlSqlFileParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class FileUploadController {

    private final PlSqlFileParserService plSqlFileParserService;

    
    /**
     * 파일 업로드를 처리하는 엔드포인트
     * 
     * @param files 업로드된 파일 배열
     * @return 성공/실패한 파일 정보를 포함한 응답
     */
    @PostMapping("/fileUpload")
    public ResponseEntity<Map<String, Object>> fileUpload(@RequestParam("files") MultipartFile[] files, HttpServletRequest request) {

        // 빈 파일 체크
        if (files.length == 0) {
            System.out.println("빈 파일이 업로드됨");
            return ResponseEntity.badRequest().body(Map.of("message", "No files uploaded"));
        }

        // 세션 정보 추출
        String sessionUUID = request.getHeader("Session-UUID");
        System.out.println("세션 UUID: " + sessionUUID);
        if (sessionUUID == null || sessionUUID.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "세션 정보가 없습니다"));
        }

        // 성공/실패 파일 목록 관리
        List<Map<String, String>> successFiles = new ArrayList<>();
        List<Map<String, String>> failedFiles = new ArrayList<>();

        // 각 파일별 처리
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                try {
                    // 파일 저장 및 정보 추출
                    Map<String, String> fileInfo = plSqlFileParserService.saveFile(file, sessionUUID);

                    
                    if (fileInfo.get("fileType").equals("PLSQL")) {
                        Map<String, String> fileData = new HashMap<>();
                        fileData.put("objectName", fileInfo.get("objectName"));
                        fileData.put("fileContent", fileInfo.get("fileContent"));
                        fileData.put("fileName", fileInfo.get("fileName"));
                        successFiles.add(fileData);
                        System.out.println("PLSQL 파일 저장 완료: " + file.getOriginalFilename() + " (오브젝트 이름: "
                            + fileInfo.get("objectName") + ")\n");
                    } else {
                        System.out.println("DDL/SEQ 파일 저장 완료: " + file.getOriginalFilename());
                    }

                } catch (Exception e) {
                    // 실패 정보 기록
                    System.out.println("파일 업로드 실패: " + file.getOriginalFilename());
                    e.printStackTrace();
                    Map<String, String> failedFileData = new HashMap<>();
                    failedFileData.put("fileName", file.getOriginalFilename());
                    failedFileData.put("error", "파일 업로드 및 저장 실패: " + e.getMessage());
                    failedFiles.add(failedFileData);
                }
            }
        }

        // 최종 응답 생성
        if (!failedFiles.isEmpty()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("successFiles", successFiles, "failedFiles", failedFiles));
        }
        return ResponseEntity.ok(Map.of("successFiles", successFiles));
    }


    /**
     * 업로드시 파일 분석을 처리하는 엔드포인트
     * 
     * @param request 분석할 파일 정보를 포함한 요청
     * @return 분석 결과 상태
     */
    @PostMapping("/analysis")
    public ResponseEntity<String> analysisContext(@RequestBody Map<String, List<Map<String, String>>> request, 
                                                HttpServletRequest httpRequest) {
        // 세션 검증
        String sessionUUID = httpRequest.getHeader("Session-UUID");
        if (sessionUUID == null || sessionUUID.trim().isEmpty()) {
            System.out.println("세션 정보 없음");
            return ResponseEntity.badRequest().body("세션 정보가 없습니다");
        }
    
        // 파일 목록 검증
        List<Map<String, String>> fileNames = request.get("fileNames");
        if (fileNames == null || fileNames.isEmpty()) {
            System.out.println("파일 정보 없음");
            return ResponseEntity.badRequest().body("파일 정보가 없습니다");
        }
    
        System.out.println("분석 시작: " + fileNames.size() + "개의 파일");
        
        // 각 파일별 분석 수행
        List<String> successFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();
    
        for (Map<String, String> fileInfo : fileNames) {
            String fileName = fileInfo.get("fileName");
            if (fileName != null && fileName.toLowerCase().endsWith(".sql")) {  // 확장자 체크    
                try {
                    plSqlFileParserService.parseAndSaveStructure(fileName, sessionUUID);
                    successFiles.add(fileName);
                    System.out.println("파일 분석 완료: " + fileName);
                } catch (Exception e) {
                    System.out.println("파일 분석 실패: " + fileName);
                    e.printStackTrace();
                    failedFiles.add(fileName);
                }
            } else {
                System.out.println("파일 확장자가 .sql이 아님: " + fileInfo.get("fileName"));
            }
        }
    
        // 결과 반환
        if (!failedFiles.isEmpty()) {
            System.out.println("일부 파일 분석 실패: " + failedFiles);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(String.format("일부 파일 분석 실패 (%d/%d 성공)", 
                        successFiles.size(), fileNames.size()));
        }
    
        System.out.println("모든 파일 분석 완료: " + successFiles.size() + "개");
        return ResponseEntity.ok("OK");
    }


    /**
     * 시작 버튼을 눌렀을 때 파일 분석을 처리하는 엔드포인트
     *
     * @return 분석된 파일 이름과 프로시저 이름이 하나의 요소로 구성된 리스트
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startAnalysisContext(HttpServletRequest httpRequest) {
        System.out.println("시작 버튼 클릭");
        
        String sessionUUID = httpRequest.getHeader("Session-UUID");
        if (sessionUUID == null || sessionUUID.trim().isEmpty()) {
            System.out.println("세션 정보 없음");
            return ResponseEntity.badRequest().body(Map.of("message", "세션 정보가 없습니다"));
        }
    
        try {
            List<Map<String, String>> successFiles = plSqlFileParserService.analyzeAllFilesInDirectory(sessionUUID);
            System.out.println("모든 파일 분석 완료: " + successFiles.size() + "개");
            return ResponseEntity.ok(Map.of("successFiles", successFiles));
        } catch (IOException e) {
            System.out.println("분석 실패: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
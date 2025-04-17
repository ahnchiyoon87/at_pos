package legacymodernizer.parser.controller;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.servlet.http.HttpServletRequest;
import legacymodernizer.parser.service.PlSqlFileParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
// @RequestMapping("/api/parser")
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
    @PostMapping("/parsing")
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
     * 테스트 샘플 처리 엔드포인트
     * 
     * @param request 파일명을 포함하는 요청
     * @param httpRequest HTTP 요청 객체 (세션 UUID 추출용)
     * @return 성공한 파일 정보 목록을 포함한 응답
     */
    @PostMapping("/testsample")
    public ResponseEntity<Map<String, Object>> testSample(@RequestBody Map<String, Object> request, 
                                                         HttpServletRequest httpRequest) {
        // 세션 검증
        String sessionUUID = httpRequest.getHeader("Session-UUID");
        if (sessionUUID == null || sessionUUID.trim().isEmpty()) {
            System.out.println("세션 정보 없음");
            return ResponseEntity.badRequest().body(Map.of("message", "세션 정보가 없습니다"));
        }
        
        // procedureName 추출
        Object procedureNameObj = request.get("procedureName");
        
        // 클라이언트에서 배열로 보내는 경우를 처리하기 위한 변환 로직
        List<String> procedureNames = new ArrayList<>();
        
        if (procedureNameObj instanceof String) {
            // 단일 문자열인 경우
            procedureNames.add((String) procedureNameObj);
            System.out.println("단일 프로시저 이름을 받음: " + procedureNameObj);
        } else if (procedureNameObj instanceof List<?>) {
            // 배열인 경우
            List<?> list = (List<?>) procedureNameObj;
            for (Object item : list) {
                if (item instanceof String) {
                    procedureNames.add((String) item);
                }
            }
            System.out.println("프로시저 이름 배열을 받음: " + procedureNames.size() + "개");
        } else {
            System.out.println("프로시저 정보 없음 또는 잘못된 형식: " + (procedureNameObj != null ? procedureNameObj.getClass().getName() : "null"));
            return ResponseEntity.badRequest().body(Map.of("message", "프로시저 정보가 없거나 잘못된 형식입니다"));
        }
        
        // 프로시저 이름 목록 검증
        if (procedureNames.isEmpty()) {
            System.out.println("처리할 프로시저 정보 없음");
            return ResponseEntity.badRequest().body(Map.of("message", "처리할 프로시저 정보가 없습니다"));
        }
        
        System.out.println("테스트 샘플 처리 시작: " + procedureNames.size() + "개의 프로시저");

        List<Map<String, String>> successFiles = new ArrayList<>();
        String errorMessage = null;
        
        try {
            // 서비스를 통해 파일 정보 조회
            successFiles = plSqlFileParserService.processTestSample(procedureNames, sessionUUID);
        } catch (Exception e) {
            System.out.println("테스트 샘플 처리 중 오류 발생");
            e.printStackTrace();
            errorMessage = "처리 중 일부 오류가 발생했습니다: " + e.getMessage();
        }
        
        if (successFiles.isEmpty()) {
            System.out.println("처리할 수 있는 파일이 없습니다.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", errorMessage != null ? errorMessage : "처리할 수 있는 파일이 없습니다."));
        }
        
        // 성공 결과 반환
        System.out.println("테스트 샘플 처리 완료: " + successFiles.size() + "개의 파일");
        Map<String, Object> response = new HashMap<>();
        response.put("successFiles", successFiles);
        if (errorMessage != null) {
            response.put("warning", errorMessage);
        }
        return ResponseEntity.ok(response);
    }

}
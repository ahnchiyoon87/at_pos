package legacymodernizer.parser.controller;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import legacymodernizer.parser.service.PlSqlFileParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class FileUploadController {

    private final PlSqlFileParserService plSqlFileParserService;

    // ========================================
    // API 엔드포인트
    // ========================================

    /**
     * 파일 업로드
     * - testmode=true: 기존 파일만 조회
     * - testmode=false: 업로드 파일 저장
     * @param metadata JSON 메타데이터 {dbms, projectName, systems, ddl, sequence, testmode}
     * @param files 업로드 파일 배열
     * @param httpRequest HTTP 요청 (Session-UUID 헤더 사용)
     * @return {dbms, successFiles}
     */
    @PostMapping(value = "/fileUpload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> fileUpload(@RequestPart("metadata") String metadata,
                                                          @RequestPart("files") MultipartFile[] files,
                                                          HttpServletRequest httpRequest) {
        String sessionUUID = httpRequest.getHeader("Session-UUID");
        log.debug("세션 UUID: {}", sessionUUID);
        if (sessionUUID == null || sessionUUID.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "세션 정보가 없습니다");
        }

        Map<String, Object> request = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            request = mapper.readValue(metadata, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "metadata 파싱 실패: " + e.getMessage());
        }

        String dbms = (String) request.getOrDefault("dbms", "");
        String projectName = (String) request.getOrDefault("projectName", "");
        Object systemsObj = request.get("systems");
        Object ddlObj = request.get("ddl");
        Object seqObj = request.get("sequence");
		boolean testMode = Boolean.TRUE.equals(request.get("testmode")) || Boolean.TRUE.equals(request.get("testMode"));

		int filesCount = files != null ? files.length : 0;
        int systemsCount = (systemsObj instanceof List<?>) ? ((List<?>) systemsObj).size() : 0;
        
        log.info("\n" +
                "================================================================================\n" +
                " [파일 업로드]\n" +
                "================================================================================\n" +
                "  DBMS: {}  |  프로젝트: {}  |  파일: {}개  |  시스템: {}개",
                dbms, projectName, filesCount, systemsCount);

		Map<String, MultipartFile> nameToFile = new HashMap<>();
		if (!testMode) {
			if (files == null || files.length == 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "files가 필요합니다 (testmode=false)");
			}
			boolean hasNonEmpty = false;
			for (MultipartFile f : files) {
				if (f == null || f.isEmpty()) continue;
				String key = f.getOriginalFilename() != null ? f.getOriginalFilename().toLowerCase() : null;
				if (key != null) {
					nameToFile.put(key, f);
					hasNonEmpty = true;
				}
			}
			if (!hasNonEmpty) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "모든 업로드 파일이 비어있습니다 (testmode=false)");
			}
		}

        Map<String, Object> result = plSqlFileParserService.processUploadByMetadata(
            sessionUUID, projectName, systemsObj, ddlObj, seqObj, nameToFile
        );
        @SuppressWarnings("unchecked")
        List<Map<String, String>> successFiles = (List<Map<String, String>>) result.get("successFiles");

        log.info("\n  업로드 완료 - 총 {}개 파일", successFiles.size());
        log.info("================================================================================\n");
        return ResponseEntity.ok(Map.of("dbms", dbms, "successFiles", successFiles));
    }

    /**
     * 파일 파싱 (ANTLR 분석)
     * @param request {dbms, projectName, systems:[{name, sp:[]}]}
     * @param httpRequest HTTP 요청 (Session-UUID 헤더 사용)
     * @return {dbms, successFiles}
     */
    @PostMapping("/parsing")
    public ResponseEntity<Map<String, Object>> analysisContext(@RequestBody Map<String, Object> request, 
                                                HttpServletRequest httpRequest) {
        String sessionUUID = httpRequest.getHeader("Session-UUID");
        if (sessionUUID == null || sessionUUID.trim().isEmpty()) {
            log.warn("[parsing] 세션 UUID가 없습니다");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "세션 정보가 없습니다");
        }
        
        String dbms = (String) request.getOrDefault("dbms", "");
        String projectName = (String) request.getOrDefault("projectName", "");
        Object systemsObj = request.get("systems");
        if (!(systemsObj instanceof List<?>)) {
            log.warn("[parsing] systems 정보가 없거나 형식이 올바르지 않습니다");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "systems 정보가 없습니다");
        }

		List<?> systems = (List<?>) systemsObj;
		Map<String, Object> result = plSqlFileParserService.processParsingBySystems(sessionUUID, projectName, systems);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> successFiles = (List<Map<String, String>>) result.get("successFiles");
		return ResponseEntity.ok(Map.of("dbms", dbms, "successFiles", successFiles));
    }
}
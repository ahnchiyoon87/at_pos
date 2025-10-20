package legacymodernizer.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import legacymodernizer.parser.controller.FileUploadController;
import legacymodernizer.parser.service.PlSqlFileParserService;

@SpringBootTest
public class AntlrAnalysisTest {
    
    @Autowired
    private FileUploadController fileUploadController;
    
    @Autowired
    private PlSqlFileParserService plSqlFileParserService;

    private MockHttpServletRequest mockRequest;
    private static final String TEST_SESSION = "TestSession_5";
    private static final String TEST_PROJECT = "TestProject_5";

    // ========================================
    // 테스트 설정
    // ========================================

    @BeforeEach
    void setUp() throws Exception {
        mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("Session-UUID", TEST_SESSION);
        
        String srcDir = plSqlFileParserService.getTargetDirectory(TEST_SESSION, TEST_PROJECT, null);
        File srcDirFile = new File(srcDir);
        if (!srcDirFile.exists()) {
            srcDirFile.mkdirs();
        }

        String analysisDir = plSqlFileParserService.getAnalysisDirectory(TEST_SESSION, TEST_PROJECT, null);
        File analysisDirFile = new File(analysisDir);
        if (analysisDirFile.exists()) {
            deleteRecursively(analysisDirFile);
        }
        System.out.println("Analysis 디렉토리 정리 완료: " + analysisDir);
    }

    // ========================================
    // 테스트 케이스
    // ========================================
    
    /**
     * 기존 파일 분석 테스트
     * - src 디렉터리의 SQL 파일들을 파싱
     * - 분석 결과 JSON 파일 생성 검증
     */
    @Test
    void testAnalysisWithExistingFiles() throws Exception {
        String srcDir = plSqlFileParserService.getTargetDirectory(TEST_SESSION, TEST_PROJECT, null);
        File srcDirFile = new File(srcDir);
        File[] sqlFiles = srcDirFile.listFiles((dir, name) -> {
            String lowercaseName = name.toLowerCase();
            return lowercaseName.endsWith(".sql") || 
                   lowercaseName.endsWith(".plsql") ||
                   lowercaseName.endsWith(".pls") ||
                   lowercaseName.endsWith(".pck") ||
                   lowercaseName.endsWith(".txt");
        });   

        assertNotNull(sqlFiles, "SQL 파일을 찾을 수 없습니다");
        assertTrue(sqlFiles.length > 0, "분석할 SQL 파일이 없습니다");
        
        List<String> spList = new ArrayList<>();
        for (File sqlFile : sqlFiles) {
            spList.add(sqlFile.getName());
        }
        Map<String, Object> system = new HashMap<>();
        system.put("name", "TEST");
        system.put("sp", spList);
        List<Map<String, Object>> systems = new ArrayList<>();
        systems.add(system);

        Map<String, Object> request = new HashMap<>();
        request.put("projectName", TEST_PROJECT);
        request.put("dbms", "oracle");
        request.put("systems", systems);

        ResponseEntity<Map<String, Object>> response = fileUploadController.analysisContext(request, mockRequest);

        assertEquals(200, response.getStatusCode().value(), "분석이 실패했습니다");
        assertTrue(response.getBody().containsKey("successFiles"), "successFiles가 없습니다");
        
        String analysisDir = plSqlFileParserService.getAnalysisDirectory(TEST_SESSION, TEST_PROJECT, null);
        for (File sqlFile : sqlFiles) {
            String baseFileName = sqlFile.getName().substring(0, sqlFile.getName().lastIndexOf('.'));
            Path found = findJsonRecursively(Paths.get(analysisDir), baseFileName + ".json");
            assertNotNull(found, String.format("분석 결과 파일이 생성되지 않았습니다: %s", baseFileName + ".json"));
            
            String content = Files.readString(found);
            assertFalse(content.isEmpty(), 
                String.format("분석 결과 파일이 비어있습니다: %s", found));
            
            System.out.println("분석 완료: " + sqlFile.getName());
            System.out.println("결과 파일: " + found);
            System.out.println("결과 내용: " + content);
            System.out.println("-------------------");
        }
    }

    // ========================================
    // 테스트 유틸리티
    // ========================================

    /**
     * 디렉터리 재귀 삭제
     */
    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }

    /**
     * JSON 파일 재귀 검색
     */
    private static Path findJsonRecursively(Path root, String fileName) throws Exception {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equalsIgnoreCase(fileName))
                .findFirst()
                .orElse(null);
        }
    }
}
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
    private static final String TEST_SESSION = "TestSession_2";
    private static final String TEST_PROJECT = "waterpoc";
    private static final String TEST_DBMS = "plsql";

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
     * 복잡한 UPDATE 서브쿼리 테스트
     * - dml_postgresql 전략을 사용하여 깊게 중첩된 서브쿼리 파싱 테스트
     */
    @Test
    void testComplexUpdateWithNestedSubqueries() throws Exception {
        String testSession = "TestSession_3";
        String testProject = "test";
        String testDbms = "dml_postgresql";
        String testSystem = "sample";
        
        MockHttpServletRequest testRequest = new MockHttpServletRequest();
        testRequest.addHeader("Session-UUID", testSession);
        
        // 테스트 디렉토리 준비
        String srcDir = plSqlFileParserService.getTargetDirectory(testSession, testProject, testSystem);
        File srcDirFile = new File(srcDir);
        if (!srcDirFile.exists()) {
            srcDirFile.mkdirs();
        }
        
        // Analysis 디렉토리 정리
        String analysisDir = plSqlFileParserService.getAnalysisDirectory(testSession, testProject, testSystem);
        File analysisDirFile = new File(analysisDir);
        if (analysisDirFile.exists()) {
            deleteRecursively(analysisDirFile);
        }
        analysisDirFile.mkdirs();
        
        // 테스트 SQL 파일 복사 (프로젝트 루트의 test_complex_update.sql을 사용)
        File testSqlFile = new File("test_complex_update.sql");
        if (!testSqlFile.exists()) {
            fail("테스트 파일이 없습니다: test_complex_update.sql");
        }
        
        File targetFile = new File(srcDir, "test_complex_update.sql");
        Files.copy(testSqlFile.toPath(), targetFile.toPath(), 
                   java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        
        System.out.println("테스트 파일 준비 완료: " + targetFile.getAbsolutePath());
        
        // 분석 요청 준비
        List<Map<String, Object>> systems = new ArrayList<>();
        Map<String, Object> system = new HashMap<>();
        system.put("name", testSystem);
        system.put("sp", List.of("test_complex_update.sql"));
        systems.add(system);
        
        Map<String, Object> request = new HashMap<>();
        request.put("projectName", testProject);
        request.put("dbms", testDbms);
        request.put("systems", systems);
        
        // 분석 실행
        ResponseEntity<Map<String, Object>> response = fileUploadController.analysisContext(request, testRequest);
        
        // 결과 검증
        assertEquals(200, response.getStatusCode().value(), "분석이 실패했습니다");
        assertTrue(response.getBody().containsKey("successFiles"), "successFiles가 없습니다");
        
        // JSON 파일 검증
        Path jsonFile = Paths.get(analysisDir, "test_complex_update.json");
        assertTrue(Files.exists(jsonFile), "분석 결과 JSON 파일이 생성되지 않았습니다: " + jsonFile);
        
        String jsonContent = Files.readString(jsonFile);
        assertFalse(jsonContent.isEmpty(), "JSON 파일이 비어있습니다");
        assertTrue(jsonContent.contains("\"type\""), "JSON에 type 필드가 없습니다");
        assertTrue(jsonContent.contains("UPDATE"), "JSON에 UPDATE 노드가 없습니다");
        
        System.out.println("========================================");
        System.out.println("복잡한 UPDATE 서브쿼리 테스트 성공!");
        System.out.println("세션: " + testSession);
        System.out.println("프로젝트: " + testProject);
        System.out.println("DBMS: " + testDbms);
        System.out.println("결과 파일: " + jsonFile);
        System.out.println("JSON 크기: " + jsonContent.length() + " bytes");
        System.out.println("========================================");
        
        // JSON 구조 간단히 출력 (처음 500자)
        if (jsonContent.length() > 500) {
            System.out.println("JSON 미리보기 (처음 500자):");
            System.out.println(jsonContent.substring(0, 500) + "...");
        } else {
            System.out.println("JSON 전체:");
            System.out.println(jsonContent);
        }
    }
    
    /**
     * 기존 파일 분석 테스트
     * - src 디렉터리의 SQL 파일들을 파싱
     * - 분석 결과 JSON 파일 생성 검증
     */
    @Test
    void testAnalysisWithExistingFiles() throws Exception {
        String srcDir = plSqlFileParserService.getTargetDirectory(TEST_SESSION, TEST_PROJECT, null);
        File srcDirFile = new File(srcDir);
        
        // src 디렉토리가 존재하는지 확인
        assertTrue(srcDirFile.exists(), "src 디렉토리가 존재하지 않습니다: " + srcDir);
        assertTrue(srcDirFile.isDirectory(), "src 경로가 디렉토리가 아닙니다: " + srcDir);
        
        // 시스템별 폴더 및 파일을 동적으로 탐지
        List<Map<String, Object>> systems = new ArrayList<>();
        Map<String, List<File>> systemToFiles = new HashMap<>();
        
        File[] systemDirs = srcDirFile.listFiles(File::isDirectory);
        assertNotNull(systemDirs, "시스템 폴더를 찾을 수 없습니다");
        assertTrue(systemDirs.length > 0, "시스템 폴더가 없습니다");
        
        // 각 폴더를 시스템명으로 인식
        for (File systemDir : systemDirs) {
            String systemName = systemDir.getName();
            File[] sqlFiles = systemDir.listFiles((dir, name) -> {
                String lowercaseName = name.toLowerCase();
                return lowercaseName.endsWith(".sql") || 
                       lowercaseName.endsWith(".plsql") ||
                       lowercaseName.endsWith(".pls") ||
                       lowercaseName.endsWith(".pck") ||
                       lowercaseName.endsWith(".txt");
            });
            
            if (sqlFiles != null && sqlFiles.length > 0) {
                List<File> fileList = new ArrayList<>();
                List<String> spList = new ArrayList<>();
                
                for (File sqlFile : sqlFiles) {
                    fileList.add(sqlFile);
                    spList.add(sqlFile.getName());
                }
                
                Map<String, Object> system = new HashMap<>();
                system.put("name", systemName);
                system.put("sp", spList);
                systems.add(system);
                systemToFiles.put(systemName, fileList);
            }
        }
        
        assertTrue(systems.size() > 0, "분석할 시스템이 없습니다");
        
        Map<String, Object> request = new HashMap<>();
        request.put("projectName", TEST_PROJECT);
        request.put("dbms", TEST_DBMS);
        request.put("systems", systems);

        ResponseEntity<Map<String, Object>> response = fileUploadController.analysisContext(request, mockRequest);

        assertEquals(200, response.getStatusCode().value(), "분석이 실패했습니다");
        assertTrue(response.getBody().containsKey("successFiles"), "successFiles가 없습니다");
        
        // 각 시스템별로 분석 결과 파일 검증
        for (Map<String, Object> system : systems) {
            String systemName = (String) system.get("name");
            List<File> files = systemToFiles.get(systemName);
            
            if (files != null) {
                String analysisDir = plSqlFileParserService.getAnalysisDirectory(TEST_SESSION, TEST_PROJECT, systemName);
                for (File sqlFile : files) {
                    String baseFileName = sqlFile.getName().substring(0, sqlFile.getName().lastIndexOf('.'));
                    Path found = findJsonRecursively(Paths.get(analysisDir), baseFileName + ".json");
                    assertNotNull(found, String.format("분석 결과 파일이 생성되지 않았습니다: system=%s, file=%s.json", systemName, baseFileName));
                    
                    String content = Files.readString(found);
                    assertFalse(content.isEmpty(), 
                        String.format("분석 결과 파일이 비어있습니다: %s", found));
                    
                    System.out.println(String.format("시스템: %s, 파일: %s", systemName, sqlFile.getName()));
                    System.out.println("결과 파일: " + found);
                    System.out.println("-------------------");
                }
            }
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
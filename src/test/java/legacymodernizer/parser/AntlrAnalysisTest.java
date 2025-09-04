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
    private static final String TEST_SESSION = "TestSession";

    @BeforeEach
    void setUp() throws Exception {
        mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("Session-UUID", TEST_SESSION);

        // analysis 폴더 내용 삭제
        String analysisDir = plSqlFileParserService.getAnalysisDirectory(TEST_SESSION);
        File analysisDirFile = new File(analysisDir);
        if (analysisDirFile.exists()) {
            for (File file : analysisDirFile.listFiles()) {
                file.delete();
            }
        }
        System.out.println("Analysis 디렉토리 정리 완료: " + analysisDir);
    }
    
    @Test
    void testAnalysisWithExistingFiles() throws Exception {
        // src 폴더의 SQL 파일 목록 가져오기
        String srcDir = plSqlFileParserService.getTargetDirectory(TEST_SESSION, null);
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
        
        // 분석 요청 데이터 준비
        List<Map<String, String>> fileNames = new ArrayList<>();
        for (File sqlFile : sqlFiles) {
            Map<String, String> fileInfo = new HashMap<>();
            fileInfo.put("fileName", sqlFile.getName());
            fileNames.add(fileInfo);
        }
        
        Map<String, List<Map<String, String>>> request = new HashMap<>();
        request.put("fileNames", fileNames);
        
        // 분석 실행
        ResponseEntity<String> response = fileUploadController.analysisContext(request, mockRequest);
        
        // 결과 검증
        assertEquals(200, response.getStatusCode().value(), "분석이 실패했습니다");
        assertEquals("OK", response.getBody(), "분석 결과가 OK가 아닙니다");
        
        // 분석 결과 파일 확인
        String analysisDir = plSqlFileParserService.getAnalysisDirectory(TEST_SESSION);
        for (File sqlFile : sqlFiles) {
            String baseFileName = sqlFile.getName().substring(0, sqlFile.getName().lastIndexOf('.'));
            Path resultPath = Paths.get(analysisDir, baseFileName + ".json");
            assertTrue(Files.exists(resultPath), 
                String.format("분석 결과 파일이 생성되지 않았습니다: %s", resultPath));
            
            String content = Files.readString(resultPath);
            assertFalse(content.isEmpty(), 
                String.format("분석 결과 파일이 비어있습니다: %s", resultPath));
            
            System.out.println("분석 완료: " + sqlFile.getName());
            System.out.println("결과 파일: " + resultPath);
            System.out.println("결과 내용: " + content);
            System.out.println("-------------------");
        }
    }
    
    @Test
    void testSqlObjectNameExtraction() throws Exception {
        System.out.println("=== SQL 객체명 추출 테스트 시작 ===");
        
        // 테스트 케이스들
        String[] testCases = {
            "CREATE OR REPLACE PROCEDURE EXWGIOS.PRC_GEN_EQUIPMENTS_DAM",
            "CREATE OR REPLACE PROCEDURE PRC_TEST_PROC",
            "CREATE OR REPLACE FUNCTION SCHEMA_NAME.FUNC_NAME",
            "CREATE OR REPLACE PACKAGE BODY PKG_BODY_NAME",
            "CREATE OR REPLACE PACKAGE OWNER.PKG_NAME"
        };
        
        String[] expectedResults = {
            "PRC_GEN_EQUIPMENTS_DAM",
            "PRC_TEST_PROC", 
            "FUNC_NAME",
            "PKG_BODY_NAME",
            "PKG_NAME"
        };
        
        // 각 테스트 케이스 검증
        for (int i = 0; i < testCases.length; i++) {
            String sqlContent = testCases[i];
            String expected = expectedResults[i];
            
            String extracted = plSqlFileParserService.extractSqlObjectName(sqlContent);
            
            assertEquals(expected, extracted, 
                String.format("테스트 실패 - SQL: %s, 예상: %s, 실제: %s", 
                    sqlContent, expected, extracted));
            
            System.out.println(String.format("✓ 성공: '%s' → '%s'", sqlContent, extracted));
        }
        
        System.out.println("=== SQL 객체명 추출 테스트 완료 ===");
    }
    
    @Test
    void testRealFileObjectNameExtraction() throws Exception {
        System.out.println("=== 실제 파일에서 객체명 추출 테스트 시작 ===");
        
        // src 폴더의 SQL 파일 목록 가져오기
        String srcDir = plSqlFileParserService.getTargetDirectory(TEST_SESSION, null);
        File srcDirFile = new File(srcDir);
        
        // 파일이 있다면 실제 파일로 테스트
        if (srcDirFile.exists()) {
            File[] sqlFiles = srcDirFile.listFiles((dir, name) -> {
                String lowercaseName = name.toLowerCase();
                return lowercaseName.endsWith(".sql") || 
                       lowercaseName.endsWith(".plsql") ||
                       lowercaseName.endsWith(".pls") ||
                       lowercaseName.endsWith(".pck");
            });
            
            if (sqlFiles != null && sqlFiles.length > 0) {
                for (File sqlFile : sqlFiles) {
                    String fileContent = plSqlFileParserService.readFileContent(sqlFile);
                    String extractedObjectName = plSqlFileParserService.extractSqlObjectName(fileContent);
                    
                    System.out.println(String.format("파일: %s", sqlFile.getName()));
                    System.out.println(String.format("추출된 객체명: %s", extractedObjectName));
                    
                    // PRC_GEN_EQUIPMENTS_DAM.sql 파일의 경우 특별히 검증
                    if ("PRC_GEN_EQUIPMENTS_DAM.sql".equals(sqlFile.getName())) {
                        assertEquals("PRC_GEN_EQUIPMENTS_DAM", extractedObjectName,
                            "PRC_GEN_EQUIPMENTS_DAM.sql에서 올바른 객체명이 추출되지 않았습니다");
                        System.out.println("✓ PRC_GEN_EQUIPMENTS_DAM.sql 객체명 추출 성공!");
                    }
                    
                    System.out.println("-------------------");
                }
            } else {
                System.out.println("테스트할 SQL 파일이 없습니다.");
            }
        } else {
            System.out.println("src 디렉토리가 존재하지 않습니다: " + srcDir);
        }
        
        System.out.println("=== 실제 파일에서 객체명 추출 테스트 완료 ===");
    }
}
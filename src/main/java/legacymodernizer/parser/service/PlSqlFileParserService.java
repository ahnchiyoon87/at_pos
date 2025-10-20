package legacymodernizer.parser.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import legacymodernizer.parser.antlr.CaseChangingCharStream;
import legacymodernizer.parser.antlr.CustomPlSqlListener;
import legacymodernizer.parser.antlr.plsql.PlSqlLexer;
import legacymodernizer.parser.antlr.plsql.PlSqlParser;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PlSqlFileParserService {

    private static final String BASE_DIR = System.getenv("DOCKER_COMPOSE_CONTEXT") != null ?
            System.getenv("DOCKER_COMPOSE_CONTEXT") :
            new File(System.getProperty("user.dir")).getParent() + File.separator + "data";

    private static final String PLSQL_DIR = "src";
    private static final String DDL_DIR = "ddl";
    private static final String SEQ_DIR = "sequence";
    private static final String ANALYSIS_DIR = "analysis";

    // ========================================
    // 경로 유틸리티
    // ========================================

    /**
     * 경로를 제거한 파일명 반환
     * @param name 파일 경로 (null 허용)
     * @return 파일명
     */
    private static String toBaseName(String name) {
        if (name == null) return null;
        return Paths.get(name).getFileName().toString();
    }

    /**
     * 확장자를 제거한 파일명 반환
     * @param name 파일명
     * @return 확장자 제외 파일명
     */
    private static String toBaseNameWithoutExt(String name) {
        String base = toBaseName(name);
        if (base == null) return null;
        int idx = base.lastIndexOf('.');
        return idx > 0 ? base.substring(0, idx) : base;
    }

    /**
     * 프로젝트 루트 디렉터리 경로 반환
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @return 프로젝트 루트 절대경로
     */
    public String getProjectRootDirectory(String sessionUUID, String projectName) throws IOException {
        if (sessionUUID == null || sessionUUID.isBlank()) {
            throw new IOException("세션 UUID가 비어있습니다");
        }
        if (projectName == null || projectName.isBlank()) {
            throw new IOException("프로젝트명이 비어있습니다");
        }
        return BASE_DIR + File.separator + sessionUUID + File.separator + projectName;
    }

    /**
     * 파일명 기반 타겟 디렉터리 경로 반환 (src/ddl/sequence 자동 구분)
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @param fileName 파일명 (DDL/SEQ 포함 시 해당 폴더)
     * @return 타겟 디렉터리 절대경로
     */
    public String getTargetDirectory(String sessionUUID, String projectName, String fileName) throws IOException {
        String subDir = PLSQL_DIR;
        if (fileName != null) {
            String upperFileName = fileName.toUpperCase();
            if (upperFileName.contains("DDL")) subDir = DDL_DIR;
            if (upperFileName.contains("SEQ")) subDir = SEQ_DIR;
        }
        return getProjectRootDirectory(sessionUUID, projectName) + File.separator + subDir;
    }

    /**
     * 분석 결과 디렉터리 경로 반환
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @param systemName 시스템명 (null 가능)
     * @return 분석 디렉터리 절대경로
     */
    public String getAnalysisDirectory(String sessionUUID, String projectName, String systemName) throws IOException {
        String base = getProjectRootDirectory(sessionUUID, projectName) + File.separator + ANALYSIS_DIR;
        if (systemName == null || systemName.isEmpty()) {
            return base;
        }
        return base + File.separator + systemName;
    }

    /**
     * 파일이 속한 버킷 판별 (src/ddl/sequence)
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @param file 파일 객체
     * @return 버킷명
     */
    public String getBucketForFile(String sessionUUID, String projectName, File file) throws IOException {
        String projectRoot = getProjectRootDirectory(sessionUUID, projectName);
        Path project = new File(projectRoot).toPath().toRealPath();
        Path path = file.toPath().toRealPath();
        Path rel = project.relativize(path);
        if (rel.getNameCount() == 0) return "unknown";
        String first = rel.getName(0).toString();
        if (DDL_DIR.equals(first)) return DDL_DIR;
        if (SEQ_DIR.equals(first)) return SEQ_DIR;
        if (PLSQL_DIR.equals(first)) return PLSQL_DIR;
        return "unknown";
    }

    /**
     * src 하위 파일의 시스템명 추출
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @param file 파일 객체
     * @return 시스템명 (추출 불가 시 null)
     */
    public String detectSystemNameForFile(String sessionUUID, String projectName, File file) throws IOException {
        Path srcPath = new File(getTargetDirectory(sessionUUID, projectName, null)).toPath().toRealPath();
        Path filePath = file.toPath().toRealPath();
        if (filePath.startsWith(srcPath)) {
            Path rel = srcPath.relativize(filePath);
            if (rel.getNameCount() >= 2) {
                return rel.getName(0).toString();
            }
        }
        return null;
    }

    // ========================================
    // 파일 I/O
    // ========================================

    /**
     * 다양한 인코딩으로 파일 읽기 (UTF-8 → EUC-KR → MS949)
     * @param file 파일 객체
     * @return 파일 내용
     */
    public String readFileContent(File file) throws IOException {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            log.debug("[readFile] 인코딩 선택 - UTF-8, file={}", file.getName());
            return content;
        } catch (MalformedInputException e) {
            try {
                String content = Files.readString(file.toPath(), Charset.forName("EUC-KR"));
                log.debug("[readFile] 인코딩 선택 - EUC-KR, file={}", file.getName());
                return content;
            } catch (Exception e2) {
                String content = Files.readString(file.toPath(), Charset.forName("MS949"));
                log.debug("[readFile] 인코딩 선택 - MS949, file={}", file.getName());
                return content;
            }
        }
    }

    /**
     * 버킷 디렉터리에 파일 저장 (src는 시스템 하위 가능)
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @param bucket 버킷명 (src/ddl/sequence)
     * @param systemName 시스템명 (src일 때만 사용, null 가능)
     * @param fileName 파일명
     * @param inputStream 파일 스트림
     * @return 저장된 파일 절대경로
     */
    public String saveToBucketFromStream(String sessionUUID,
                                         String projectName,
                                         String bucket,
                                         String systemName,
                                         String fileName,
                                         InputStream inputStream) throws IOException {
        String baseFileName = toBaseName(fileName);
        String projectRoot = getProjectRootDirectory(sessionUUID, projectName);
        File targetDir = new File(projectRoot, bucket);
        if (PLSQL_DIR.equals(bucket) && systemName != null && !systemName.isBlank()) {
            targetDir = new File(targetDir, systemName);
        }
        createDirectoryIfNotExists(targetDir.getAbsolutePath());
        File out = new File(targetDir, baseFileName);
        Files.copy(inputStream, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return out.getAbsolutePath();
    }

    /**
     * 디렉터리 생성 (존재하지 않을 때만)
     * @param path 디렉터리 경로
     */
    private void createDirectoryIfNotExists(String path) throws IOException {
        Path dirPath = Paths.get(path);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
            log.debug("      → 디렉터리 생성: {}", dirPath.getFileName());
        }
    }

    // ========================================
    // 파싱 및 분석
    // ========================================

    /**
     * ANTLR 파싱 후 분석 결과를 JSON으로 저장
     * @param candidate 파싱 대상 파일
     * @param displayFileName 표시용 파일명
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @param systemName 시스템명
     */
    private void parseAndSaveStructure(File candidate,
                                       String displayFileName,
                                       String sessionUUID,
                                       String projectName,
                                       String systemName) throws IOException {
        String analysisDir = getAnalysisDirectory(sessionUUID, projectName, systemName);
        String baseFileName = toBaseNameWithoutExt(displayFileName != null ? displayFileName : candidate.getName());
        String outputPath = analysisDir + File.separator + baseFileName + ".json";

        createDirectoryIfNotExists(analysisDir);

        log.debug("      [ANTLR 파싱 시작]");
        try (InputStream in = new FileInputStream(candidate)) {
            CharStream s = CharStreams.fromStream(in);
            CaseChangingCharStream upper = new CaseChangingCharStream(s, true);
            PlSqlLexer lexer = new PlSqlLexer(upper);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            PlSqlParser parser = new PlSqlParser(tokens);
            ParserRuleContext tree = parser.sql_script();
            CustomPlSqlListener listener = new CustomPlSqlListener(tokens);
            new ParseTreeWalker().walk(listener, tree);
            File analysisFile = new File(outputPath);
            try (FileWriter file = new FileWriter(analysisFile)) {
                file.write(listener.getRoot().toJson());
            }
            log.debug("      → 분석 결과 저장: {}", analysisFile.getName());
        }
    }

    /**
     * 분석 결과 파일 존재 여부 확인
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @param systemName 시스템명
     * @param fileName 파일명
     * @return 존재 여부
     */
    public boolean analysisExists(String sessionUUID, String projectName, String systemName, String fileName) throws IOException {
        String analysisDir = getAnalysisDirectory(sessionUUID, projectName, systemName);
        String base = toBaseName(fileName);
        String baseNoExt = base.contains(".") ? base.substring(0, base.lastIndexOf('.')) : base;
        File analysisFile = new File(analysisDir, baseNoExt + ".json");
        return analysisFile.exists();
    }

    /**
     * 필요 시 SP 파일 분석 실행
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @param systemNameHint 시스템명 힌트
     * @param located 파일 객체
     * @return 분석 실행 여부
     */
    private boolean analyzeSpIfNeeded(String sessionUUID, String projectName, String systemNameHint, File located) throws IOException {
        String bucket = getBucketForFile(sessionUUID, projectName, located);
        if (!PLSQL_DIR.equals(bucket)) {
            return false;
        }

        String systemName = systemNameHint != null ? systemNameHint : detectSystemNameForFile(sessionUUID, projectName, located);
        boolean already = analysisExists(sessionUUID, projectName, systemName, located.getName());
        if (!already) {
            parseAndSaveStructure(located, located.getName(), sessionUUID, projectName, systemName);
            return true;
        }
        return false;
    }

    // ========================================
    // 업로드 처리
    // ========================================

    /**
     * 메타데이터 기반 파일 업로드 처리
     * - 시스템 SP: 존재하면 읽기, 없으면 업로드 파일 저장
     * - DDL/SEQ: 업로드 파일 저장
     * - 실패 시 RuntimeException 발생
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @param systemsObj systems 배열
     * @param ddlObj ddl 배열
     * @param seqObj sequence 배열
     * @param nameToFile 업로드 파일 맵 (파일명 소문자 → MultipartFile)
     * @return {successFiles}
     * @throws RuntimeException 업로드 처리 실패 시
     */
    public Map<String, Object> processUploadByMetadata(String sessionUUID,
                                                       String projectName,
                                                       Object systemsObj,
                                                       Object ddlObj,
                                                       Object seqObj,
                                                       Map<String, MultipartFile> nameToFile) {
        List<Map<String, String>> successFiles = new ArrayList<>();

        Map<String, File> fileIndex = buildProjectFileIndex(sessionUUID, projectName);

        if (systemsObj instanceof List<?>) {
            for (Object sys : (List<?>) systemsObj) {
                if (!(sys instanceof Map<?, ?>)) continue;
                Map<?, ?> sysMap = (Map<?, ?>) sys;
                String systemName = (String) sysMap.get("name");
                Object spObj = sysMap.get("sp");
                if (systemName == null || systemName.isBlank() || !(spObj instanceof List<?>)) continue;
                for (Object sp : (List<?>) spObj) {
                    if (!(sp instanceof String)) continue;
                    String fileName = (String) sp;
                    try {
                        // 기존 파일 우선 사용
                        File found = fileIndex.getOrDefault(toBaseName(fileName).toLowerCase(), null);
                        if (found != null) {
                            Map<String, String> ok = new HashMap<>();
                            ok.put("system", systemName);
                            ok.put("fileName", found.getName());
                            ok.put("filePath", found.getAbsolutePath());
                            ok.put("fileContent", readFileContent(found));
                            ok.put("analysisExists", analysisExists(sessionUUID, projectName, systemName, found.getName()) ? "true" : "false");
                            successFiles.add(ok);
                            continue;
                        }

                        // 업로드 파일 저장
                        MultipartFile mf = nameToFile != null ? nameToFile.getOrDefault(fileName.toLowerCase(), null) : null;
                        if (mf == null || mf.isEmpty()) {
                            throw new RuntimeException("파일이 존재하지 않으며 업로드 파일도 없습니다: system=" + systemName + ", file=" + fileName);
                        }

                        String savedPath = saveToBucketFromStream(sessionUUID, projectName, PLSQL_DIR, systemName, fileName, mf.getInputStream());
                        Map<String, String> ok = new HashMap<>();
                        ok.put("system", systemName);
                        ok.put("fileName", fileName);
                        ok.put("filePath", savedPath);
                        ok.put("fileContent", readFileContent(new File(savedPath)));
                        ok.put("analysisExists", analysisExists(sessionUUID, projectName, systemName, fileName) ? "true" : "false");
                        successFiles.add(ok);
                    } catch (Exception e) {
                        throw new RuntimeException("업로드 처리 실패: system=" + systemName + ", file=" + fileName + " - " + e.getMessage(), e);
                    }
                }
            }
        }

        saveBucketListFromMap(sessionUUID, projectName, DDL_DIR, ddlObj, nameToFile);
        saveBucketListFromMap(sessionUUID, projectName, SEQ_DIR, seqObj, nameToFile);

        return Map.of("successFiles", successFiles);
    }

    /**
     * 시스템별 SP 파싱 처리
     * - 실패 시 RuntimeException 발생
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @param systems systems 배열
     * @return {successFiles}
     * @throws RuntimeException 파싱 실패 시
     */
    public Map<String, Object> processParsingBySystems(String sessionUUID,
                                                       String projectName,
                                                       List<?> systems) {
        List<Map<String, String>> successFiles = new ArrayList<>();

        Map<String, File> fileIndex = buildProjectFileIndex(sessionUUID, projectName);

        if (systems == null) return Map.of("successFiles", successFiles);

        for (Object sys : systems) {
            if (!(sys instanceof Map<?, ?>)) continue;
            Map<?, ?> sysMap = (Map<?, ?>) sys;
            String systemName = (String) sysMap.get("name");
            Object spObj = sysMap.get("sp");
            if (systemName == null || !(spObj instanceof List<?>)) continue;

            List<?> spArr = (List<?>) spObj;
            for (Object sp : spArr) {
                if (!(sp instanceof String)) continue;
                String fileName = (String) sp;
                long start = System.currentTimeMillis();
                File located;
                try {
                    located = locateFileByName(sessionUUID, projectName, fileName, fileIndex);
                } catch (IOException io) {
                    throw new RuntimeException("파일 검색 실패: system=" + systemName + ", file=" + fileName + " - " + io.getMessage(), io);
                }
                if (located != null) {
                    try {
                        analyzeSpIfNeeded(sessionUUID, projectName, systemName, located);
                        Map<String, String> info = getFileInfoForFile(sessionUUID, projectName, located);
                        Map<String, String> ok = new HashMap<>();
                        ok.put("system", systemName);
                        ok.put("fileName", info.getOrDefault("fileName", fileName));
                        ok.put("fileContent", info.getOrDefault("fileContent", ""));
                        ok.put("analysisExists", info.getOrDefault("analysisExists", "false"));
                        successFiles.add(ok);
                    } catch (Exception e) {
                        throw new RuntimeException("파싱 실패: system=" + systemName + ", file=" + fileName + " - " + e.getMessage(), e);
                    }
                } else {
                    throw new RuntimeException("파일을 찾을 수 없습니다: " + fileName);
                }
                long elapsed = System.currentTimeMillis() - start;
                log.info("  {} ({}ms)", fileName, elapsed);
            }
        }

        return Map.of("successFiles", successFiles);
    }

    /**
     * DDL/SEQ 버킷에 파일 저장
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @param bucket 버킷명
     * @param listObj 파일명 배열
     * @param nameToFile 업로드 파일 맵
     */
    private void saveBucketListFromMap(String sessionUUID,
                                       String projectName,
                                       String bucket,
                                       Object listObj,
                                       Map<String, MultipartFile> nameToFile) {
        if (!(listObj instanceof List<?>) || nameToFile == null || nameToFile.isEmpty()) return;
        for (Object o : (List<?>) listObj) {
            if (!(o instanceof String)) continue;
            String fileName = (String) o;
            MultipartFile mf = nameToFile.getOrDefault(fileName.toLowerCase(), null);
            if (mf == null || mf.isEmpty()) continue;
            try {
                saveToBucketFromStream(sessionUUID, projectName, bucket, null, fileName, mf.getInputStream());
            } catch (Exception e) {
                throw new RuntimeException("버킷 저장 중 오류: " + bucket + "/" + fileName + " - " + e.getMessage(), e);
            }
        }
    }

    // ========================================
    // 파일 검색 및 인덱싱
    // ========================================

    /**
     * 프로젝트 전체 파일 인덱스 생성 (src 재귀 + ddl + sequence)
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @return 파일명(소문자) → File 맵
     */
    private Map<String, File> buildProjectFileIndex(String sessionUUID, String projectName) {
        Map<String, File> index = new HashMap<>();
        try {
            String srcRoot = getTargetDirectory(sessionUUID, projectName, null);
            File srcRootDir = new File(srcRoot);
            if (srcRootDir.exists()) {
                try (java.util.stream.Stream<Path> stream = Files.walk(srcRootDir.toPath())) {
                    stream.filter(Files::isRegularFile)
                          .map(Path::toFile)
                          .forEach(f -> index.putIfAbsent(f.getName().toLowerCase(), f));
                }
            }
            String projectRoot = getProjectRootDirectory(sessionUUID, projectName);
            File ddlDir = new File(projectRoot, DDL_DIR);
            if (ddlDir.exists()) {
                File[] list = ddlDir.listFiles();
                if (list != null) {
                    for (File f : list) {
                        if (f.isFile()) index.putIfAbsent(f.getName().toLowerCase(), f);
                    }
                }
            }
            File seqDir = new File(projectRoot, SEQ_DIR);
            if (seqDir.exists()) {
                File[] list = seqDir.listFiles();
                if (list != null) {
                    for (File f : list) {
                        if (f.isFile()) index.putIfAbsent(f.getName().toLowerCase(), f);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("프로젝트 파일 인덱스 생성 실패: " + e.getMessage(), e);
        }
        return index;
    }

    /**
     * 인덱스 기반 파일 검색 (fallback: 재귀 탐색)
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @param fileName 파일명
     * @param fileIndex 파일 인덱스
     * @return 파일 객체 (없으면 null)
     */
    private File locateFileByName(String sessionUUID, String projectName, String fileName, Map<String, File> fileIndex) throws IOException {
        String key = toBaseName(fileName);
        if (key != null) {
            File byIndex = fileIndex.getOrDefault(key.toLowerCase(), null);
            if (byIndex != null) return byIndex;
        }
        return findExistingSqlFile(sessionUUID, projectName, fileName);
    }

    /**
     * SQL 파일 재귀 검색 (src → ddl → sequence 순서)
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @param fileName 파일명
     * @return 파일 객체 (없으면 null)
     */
    private File findExistingSqlFile(String sessionUUID, String projectName, String fileName) throws IOException {
        String baseFileName = toBaseName(fileName);
        String srcRoot = getTargetDirectory(sessionUUID, projectName, null);
        File srcRootDir = new File(srcRoot);
        File candidate = findFileRecursivelyByNameIgnoreCase(srcRootDir, baseFileName);
        if (candidate != null) return candidate;

        String projectRoot = getProjectRootDirectory(sessionUUID, projectName);
        File inDdl = new File(projectRoot + File.separator + DDL_DIR, baseFileName);
        if (inDdl.exists() && inDdl.isFile()) return inDdl;
        File inSeq = new File(projectRoot + File.separator + SEQ_DIR, baseFileName);
        if (inSeq.exists() && inSeq.isFile()) return inSeq;
        return null;
    }

    /**
     * 디렉터리 재귀 검색 (대소문자 무시)
     * @param root 시작 디렉터리
     * @param fileName 파일명
     * @return 파일 객체 (없으면 null)
     */
    private File findFileRecursivelyByNameIgnoreCase(File root, String fileName) {
        if (root == null || !root.exists() || fileName == null) return null;
        try {
            try (java.util.stream.Stream<Path> stream = Files.walk(root.toPath())) {
                return stream
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(f -> f.getName().equalsIgnoreCase(fileName))
                    .findFirst()
                    .orElse(null);
            }
        } catch (IOException e) {
            throw new RuntimeException("디렉터리 검색 실패: " + root.getAbsolutePath() + " - " + e.getMessage(), e);
        }
    }

    /**
     * 파일명으로 파일 정보 조회
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @param fileName 파일명
     * @return {fileName, fileContent, analysisExists}
     */
    public Map<String, String> getFileInfoByName(String sessionUUID, String projectName, String fileName) throws IOException {
        File file = findExistingSqlFile(sessionUUID, projectName, fileName);
        if (file == null) throw new IOException("파일을 찾을 수 없습니다: " + fileName);
        return getFileInfoForFile(sessionUUID, projectName, file);
    }

    /**
     * 파일 객체로 파일 정보 생성
     * @param sessionUUID 세션 UUID
     * @param projectName 프로젝트명
     * @param file 파일 객체
     * @return {fileName, fileContent, analysisExists}
     */
    private Map<String, String> getFileInfoForFile(String sessionUUID, String projectName, File file) throws IOException {
        String fileContent = readFileContent(file);
        String systemName = detectSystemNameForFile(sessionUUID, projectName, file);
        boolean exists = analysisExists(sessionUUID, projectName, systemName, file.getName());
        return makeFileInfo(file.getName(), fileContent, exists);
    }

    /**
     * 파일 정보 맵 생성
     * @param fileName 파일명
     * @param fileContent 파일 내용
     * @param analysisExists 분석 존재 여부
     * @return 파일 정보 맵
     */
    private Map<String, String> makeFileInfo(String fileName, String fileContent, boolean analysisExists) {
        Map<String, String> map = new HashMap<>();
        map.put("fileName", fileName != null ? fileName : "");
        map.put("fileContent", fileContent != null ? fileContent : "");
        map.put("analysisExists", String.valueOf(analysisExists));
        return map;
    }
}

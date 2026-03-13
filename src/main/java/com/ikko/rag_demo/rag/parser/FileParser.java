package com.ikko.rag_demo.rag.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

@Component
public class FileParser {
    public Document parseToDocument(File file, String originalFilename) throws Exception {
        try (InputStream inputStream = new FileInputStream(file)) {
            DocumentParser parser = new ApacheTikaDocumentParser();
            Document document = parser.parse(inputStream);
            // 打上统一的元数据溯源标签
            document.metadata().add("file_name", originalFilename);
            document.metadata().add("file_path", file.getAbsolutePath());
            return document;
        }
    }
}
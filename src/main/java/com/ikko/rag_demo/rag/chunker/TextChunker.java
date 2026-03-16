package com.ikko.rag_demo.rag.chunker;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {
    // 后续如果有更复杂的按照标点符号、段落切分的逻辑，都封装在这个类里
    public DocumentSplitter getSplitter() {

        return DocumentSplitters.recursive(800, 100);
    }
}
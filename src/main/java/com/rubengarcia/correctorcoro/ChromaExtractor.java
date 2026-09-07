package com.rubengarcia.correctorcoro;

import java.io.File;
import java.util.List;

public interface ChromaExtractor {

    List<ChromaFrame> extract(File audioFile);
}

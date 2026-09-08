package com.rubengarcia.correctorcoro;

import java.util.List;

public interface AlignmentStrategy {

    AlignmentResult align(
            List<ChromaFrame> reference,
            List<ChromaFrame> performance
    );
}

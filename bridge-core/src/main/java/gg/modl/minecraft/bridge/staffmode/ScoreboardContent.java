package gg.modl.minecraft.bridge.staffmode;

import lombok.Value;

import java.util.List;

@Value
public class ScoreboardContent {
    String title;
    List<Line> lines;

    @Value
    public static class Line {
        String text;
        int score;
    }
}

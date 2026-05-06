package com.example.docbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private final Retrieval retrieval = new Retrieval();
    private final Ingestion ingestion = new Ingestion();
    private final Metrics metrics = new Metrics();

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public Ingestion getIngestion() {
        return ingestion;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public static class Retrieval {
        private int topK = 3;
        private double similarityThreshold = 0.40;
        private int maxChunkContextChars = 900;
        private int maxTotalContextChars = 2800;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }

        public int getMaxChunkContextChars() {
            return maxChunkContextChars;
        }

        public void setMaxChunkContextChars(int maxChunkContextChars) {
            this.maxChunkContextChars = maxChunkContextChars;
        }

        public int getMaxTotalContextChars() {
            return maxTotalContextChars;
        }

        public void setMaxTotalContextChars(int maxTotalContextChars) {
            this.maxTotalContextChars = maxTotalContextChars;
        }
    }

    public static class Ingestion {
        private int chunkSize = 1500;
        private int chunkOverlap = 200;

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }
    }

    public static class Metrics {
        private int rollingWindow = 20;

        public int getRollingWindow() {
            return rollingWindow;
        }

        public void setRollingWindow(int rollingWindow) {
            this.rollingWindow = rollingWindow;
        }
    }
}

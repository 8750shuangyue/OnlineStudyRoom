package com.studyroom.document;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量向量检索：把文本映射为「字符双字组 + 英文单词」的词法特征向量，按余弦相似度召回。
 * 优点：完全本地、离线、确定，不依赖外部 embedding 服务；后续可平滑替换为神经 embedding 向量库。
 */
@Service
public class VectorSearchService {

    public record ChunkHit(String chunk, double score) {
    }

    public List<ChunkHit> searchTop(List<String> chunks, String question, int limit) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        List<String> corpus = new ArrayList<>(chunks);
        corpus.add(question);
        Map<String, Integer> vocabulary = buildVocabulary(corpus);
        double[] questionVector = vectorize(question, vocabulary);
        return chunks.stream()
                .map(chunk -> new ChunkHit(chunk, cosine(questionVector, vectorize(chunk, vocabulary))))
                .sorted(Comparator.comparingDouble(ChunkHit::score).reversed())
                .limit(limit)
                .toList();
    }

    private Map<String, Integer> buildVocabulary(List<String> texts) {
        Map<String, Integer> vocabulary = new HashMap<>();
        int index = 0;
        for (String text : texts) {
            for (String feature : features(text)) {
                if (!vocabulary.containsKey(feature)) {
                    vocabulary.put(feature, index++);
                }
            }
        }
        return vocabulary;
    }

    private double[] vectorize(String text, Map<String, Integer> vocabulary) {
        Map<String, Integer> counts = new HashMap<>();
        for (String feature : features(text)) {
            counts.merge(feature, 1, Integer::sum);
        }
        double[] vector = new double[vocabulary.size()];
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            Integer index = vocabulary.get(entry.getKey());
            if (index != null) {
                vector[index] = 1 + Math.log(entry.getValue());
            }
        }
        return normalize(vector);
    }

    private List<String> features(String text) {
        List<String> features = new ArrayList<>();
        String lower = text.toLowerCase();
        for (int i = 0; i + 1 < lower.length(); i++) {
            features.add(lower.substring(i, i + 2));
        }
        for (String word : lower.split("[^a-z0-9]+")) {
            if (word.length() >= 2) {
                features.add("w:" + word);
            }
        }
        return features;
    }

    private double[] normalize(double[] vector) {
        double norm = 0;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
        return vector;
    }

    private double cosine(double[] a, double[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }
}

package searchengine.processors;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import java.io.IOException;
import java.util.*;

public class LemmaFinder {
    private final LuceneMorphology luceneMorphology; //Используется для морфологического анализа текста
    private static final String WORD_TYPE_REGEX = "\\W\\w&&[^а-яА-Я\\s]"; //Регулярное выражение для ужаление нерусских символов в тексте
    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"}; //массив строк, который содержит названия частиц русского языка.

    public static LemmaFinder getInstance() throws IOException { //Создаёт и возвращает экземпляр класса
        LuceneMorphology morphology= new RussianLuceneMorphology();
        return new LemmaFinder(morphology);
    }

    public LemmaFinder(LuceneMorphology luceneMorphology) {
        this.luceneMorphology = luceneMorphology;
    }

    public Map<String, Integer> collectLemmas(String text) { //Разделяет текст на слова, находит все леммы и считает их количество
        String[] words = arrayContainsRussianWords(text);
        HashMap<String, Integer> lemmas = new HashMap<>();

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
            if (anyWordBaseBelongToParticle(wordBaseForms)) {
                continue;
            }
            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if (normalForms.isEmpty()) {
                continue;
            }
            String normalWord = normalForms.get(0);
            if (lemmas.containsKey(normalWord)) {
                lemmas.put(normalWord, lemmas.get(normalWord) + 1);
            } else {
                lemmas.put(normalWord, 1);
            }
        }
        return lemmas;
    }

    public Set<String> getLemmaSet(String text) { //Собирает все уникальные леммы из текста и возвращает список
        String[] textArray = arrayContainsRussianWords(text);
        Set<String> lemmaSet = new HashSet<>();
        for (String word : textArray) {
            if (!word.isEmpty() && isCorrectWordForm(word)) {
                List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
                if (anyWordBaseBelongToParticle(wordBaseForms)) {
                    continue;
                }
                lemmaSet.addAll(luceneMorphology.getNormalForms(word));
            }
        }
        return lemmaSet;
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) { //Проверяет, является ли слово частьюцей русского языка
        return wordBaseForms.stream()
                .anyMatch(this::hasParticleProperty);
    } //Если хотя бы одно слово в списке является частьюцей русского языка возвращает true, иначе - false.

    private boolean hasParticleProperty(String wordBase) { //Проверяет, содержит ли строка частицу русского языка
        for (String property : particlesNames) {
            if (wordBase.toUpperCase() //Преобразуем слово в верхний регистр, чтобы избежать чувствительности к нему
                    .contains(property)) {
                return true;
            }
        }
        return false; //Если слово содержит элемент массива(particlesNames), метод возвращает true, иначе - false
    }

    private String[] arrayContainsRussianWords(String text) { //Разделяет текст на слова и удаляет нерусские символы
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^а-я\\s])", " ")
                .trim()
                .split("\\s+");
//Преобразуем в нижний регистр, меняем все нерусские символы на пробелы, удаляем пробелы в начале и конце и разделяем строку на массив строк по пробелам
    }

    private boolean isCorrectWordForm(String word) { //Проверяет, является ли слово корректной формой слова
        List<String> wordInfo = luceneMorphology.getMorphInfo(word);
        for (String morphInfo : wordInfo) {
            if (morphInfo.matches(WORD_TYPE_REGEX)) {
                return false;
            }
        } //Если морфологическая информация соответствует регулярному выражению, метод возвращает false, иначе - true
        return true;
    }
}

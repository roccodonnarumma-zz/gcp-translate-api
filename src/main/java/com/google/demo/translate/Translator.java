/*
    Copyright 2017, Google, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package com.google.demo.translate;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.Translate.TranslateOption;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class Translator {

    private static Translate translate = TranslateOptions.getDefaultInstance().getService();

    private static String source;
    private static List<String> targets;

    private static LineIterator it;
    private static Path output;

    public static void main(String[] args) {
        parseInputs();

        try {
            String headers = String.join(
                    ",",
                    source,
                    targets.stream().map(i -> i.toString()).collect(Collectors.joining(",")));

            Files.write(output, Arrays.asList(headers), UTF_8, APPEND, CREATE);

            List<String> texts = new ArrayList<>();
            while (it.hasNext()) {
                texts.add(preTranslationParser(it.next()));
                if (texts.size() == 10 || !it.hasNext()) {
                    translate(texts);
                    texts = new ArrayList<>();
                }
            }
        } catch(IOException e) {
            throw new RuntimeException(e);
        } finally {
            LineIterator.closeQuietly(it);
        }
    }

    private static void parseInputs() {
        Properties prop = new Properties();
        try {
            prop.load(Translator.class.getClassLoader().getResourceAsStream("languages.properties"));
            it = FileUtils.lineIterator(
                    new File(Translator.class.getClassLoader().getResource("input.csv").getPath()),
                    "UTF-8"
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        source = prop.getProperty("source");
        String targetsList = prop.getProperty("targets");

        targets = new ArrayList<>(Arrays.asList(targetsList.split(",")));

        String path = Translator.class.getClassLoader().getResource("").getPath() + "output.csv";
        output = Paths.get(path);
    }

    private static void translate(List<String> texts) throws IOException {
        List<String> lines = new ArrayList<>();
        for(String text : texts) {
            lines.add(postTranslationParser(text));
        }

        for(String target : targets) {
            List<Translation> translations = translate.translate(
                    texts,
                    TranslateOption.sourceLanguage(source),
                    TranslateOption.targetLanguage(target));

            List<String> results = new ArrayList<>();
            for(int i = 0; i < lines.size(); i++) {
                String translation = postTranslationParser(translations.get(i).getTranslatedText());
                results.add(String.join(";", lines.get(i), translation));
            }

            lines = results;
        }

        Files.write(output, lines, UTF_8, APPEND);
    }

    private static String preTranslationParser(String string) {
        String result = string;

        result = result.replaceAll("&", "and");
        result = result.replaceAll("\"", "");

        return result;
    }

    private static String postTranslationParser(String string) {
        if(string.contains(",")) {
            return "\"" + string + "\"";
        }

        return string;
    }
}

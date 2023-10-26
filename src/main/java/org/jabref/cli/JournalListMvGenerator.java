package org.jabref.cli;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jabref.logic.journals.Abbreviation;
import org.jabref.logic.journals.JournalAbbreviationLoader;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.jooq.lambda.Unchecked;

public class JournalListMvGenerator {

    private MVStore store;
    private MVMap<String, Abbreviation> fullToAbbreviation;
    private MVMap<String, Integer> viewCounts;

    public JournalListMvGenerator() {
        Path journalListMvFile = Path.of("build", "resources", "main", "journals", "journal-list.mv");
        store = new MVStore.Builder()
                .fileName(journalListMvFile.toString())
                .compressHigh()
                .open();
        fullToAbbreviation = store.openMap("FullToAbbreviation");
        viewCounts = store.openMap("ViewCounts");
    }

    public void updateViewCount(String citationKey) {
        Integer currentCount = viewCounts.getOrDefault(citationKey, 0);
        viewCounts.put(citationKey, currentCount + 1);
    }

    public int getViewCount(String citationKey) {
        return viewCounts.getOrDefault(citationKey, 0);
    }

    public void generate() throws IOException {
        Path abbreviationsDirectory = Path.of("buildres", "abbrv.jabref.org", "journals");
        if (!Files.exists(abbreviationsDirectory)) {
            System.out.println("Path " + abbreviationsDirectory.toAbsolutePath() + " does not exist");
            System.exit(0);
        }

        Set<String> ignoredNames = Set.of(
                "journal_abbreviations_entrez.csv",
                "journal_abbreviations_medicus.csv",
                "journal_abbreviations_webofscience-dotless.csv",
                "journal_abbreviations_ieee_strings.csv"
        );

        Files.createDirectories(abbreviationsDirectory.getParent());

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(abbreviationsDirectory, "*.csv")) {
            stream.forEach(Unchecked.consumer(path -> {
                String fileName = path.getFileName().toString();
                System.out.print("Checking ");
                System.out.print(fileName);
                if (ignoredNames.contains(fileName)) {
                    System.out.println(" ignored");
                } else {
                    System.out.println("...");
                    Collection<Abbreviation> abbreviations = JournalAbbreviationLoader.readAbbreviationsFromCsvFile(path);
                    Map<String, Abbreviation> abbreviationMap = abbreviations
                            .stream()
                            .collect(Collectors.toMap(
                                    Abbreviation::getName,
                                    abbreviation -> abbreviation,
                                    (abbreviation1, abbreviation2) -> abbreviation2
                            ));
                    fullToAbbreviation.putAll(abbreviationMap);
                }
            }));
        }
    }

    public static void main(String[] args) throws IOException {
        boolean verbose = (args.length == 1) && ("--verbose".equals(args[0]));

        JournalListMvGenerator generator = new JournalListMvGenerator();
        generator.generate();

        // Example usage: Update the view count for a citation key
        if (verbose) {
            generator.updateViewCount("exampleCitationKey");
            int count = generator.getViewCount("exampleCitationKey");
            System.out.println("View count for exampleCitationKey: " + count);
        }

        generator.store.close();
    }
}

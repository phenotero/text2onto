/**
 * 
 */
package de.phenomics;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.odftoolkit.simple.TextDocument;

/**
 * Software to generate a flat-file of ontology-terms for a set of docx or odt
 * files contained in a folder and/or subfolders.
 * 
 * 
 * @author Sebastian Köhler (dr.sebastian.koehler@gmail.com)
 *
 */
public class Text2Hpo {

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {

		Options options = new Options();

		Option input = new Option("d", "directory", true, "input directory path");
		input.setRequired(true);
		options.addOption(input);

		Option verboseOpt = new Option("v", "verbose", false, "be verbose");
		options.addOption(verboseOpt);

		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd;

		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			System.out.println(e.getMessage());
			formatter.printHelp("java -jar phenoterotext2hpo.jar", options);
			System.exit(1);
			return;
		}

		String inputFilePath = cmd.getOptionValue("directory");
		File f = new File(inputFilePath);
		if (!Files.isDirectory(f.toPath())) {
			System.out.println("no valid directory given!");
			formatter.printHelp("java -jar phenoterotext2hpo.jar", options);
			System.exit(1);
			return;
		}

		boolean verbose = cmd.hasOption("verbose");

		Text2Hpo t2hpo = new Text2Hpo(inputFilePath, verbose);

		t2hpo.processDir();

	}

	private void processDir() throws IOException {

		String outFile = inputFilePath + File.separatorChar + "annotations.hpo.tsv";

		BufferedWriter out = new BufferedWriter(new FileWriter(outFile));

		Path root = Paths.get(inputFilePath);
		Files.walk(root, Integer.MAX_VALUE).filter(f -> f.getFileName().toString().endsWith(".docx") || f.getFileName().toString().endsWith(".odt"))
				.forEach(filePath -> {
					try {
						processFile(filePath, out);
					} catch (IOException e) {
						System.err.println("error processing file: " + filePath);
						e.printStackTrace();
					}
				});
		out.close();
		System.out.println("done.\nWritten results to " + outFile);

	}

	private void processFile(Path filePath, BufferedWriter out) throws IOException {
		System.out.println(" Processing " + filePath);
		File file = filePath.toFile();
		// get the content of the file
		String textContent = getTextContent(file);
		// parse the OBO-ids, e.g. HP:0000118
		HashSet<String> oboTermIds = getOboTermIds(textContent);
		out.write(file.toString() + "\t" + String.join(",", oboTermIds) + "\n");
	}

	private HashSet<String> getOboTermIds(String textContent) {

		HashSet<String> foundOntologyTermIds = new HashSet<>();
		Pattern pattern = Pattern.compile("\\w{2,5}?:\\d+");
		Matcher m = pattern.matcher(textContent);

		while (m.find()) {
			foundOntologyTermIds.add(m.group());
		}

		return foundOntologyTermIds;
	}

	private String getTextContent(File file) {

		if (file.getAbsolutePath().endsWith(".docx")) {

			if (!file.getName().matches("^[a-zA-Z0-9].*$")) {
				System.out.println(" ... skipping " + file);
				return null;
			}

			try {
				FileInputStream fis = new FileInputStream(file.getAbsolutePath());
				XWPFDocument clinicalDocument = new XWPFDocument(fis);
				XWPFWordExtractor extractor = new XWPFWordExtractor(clinicalDocument);
				return extractor.getText();
			} catch (IOException e) {
				e.printStackTrace();
			}

		} else if (file.getAbsolutePath().endsWith(".odt")) {

			try {
				TextDocument document = TextDocument.loadDocument(file.getAbsolutePath());
				return document.getContentRoot().getTextContent();
			} catch (Exception e) {
				e.printStackTrace();
			}

		} else {
			System.err.println("cannot handle file type of file: " + file);
		}

		return null;
	}

	private String inputFilePath;
	private boolean verbose;

	public Text2Hpo(String inputFilePath, boolean verbose) {
		this.inputFilePath = inputFilePath;
		this.verbose = verbose;
	}

}

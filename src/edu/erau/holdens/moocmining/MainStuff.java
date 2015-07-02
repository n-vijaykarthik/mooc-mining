package edu.erau.holdens.moocmining;

import static edu.erau.holdens.moocmining.Utils.getFullFileText;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

/**
 * @author Sean Holden (holdens@my.erau.edu), with some derivative code (original word counting code) from Nick Brixius (brixiusn@erau.edu)
 */
public class MainStuff {

	/** Excel File from which to populate the COCA map */
	private static final File COCA_FILE = new File("data/allWords.xls");	// TODO learn to use XSSF for xlsx (or not...)

	public static void main(String[] args) throws IOException {		

		scanFile(new File("data/text.txt"));

	}

	/** Scans a file.  Any output is currently written to the console.
	 * @param f The file to scan
	 * @throws IOException
	 */
	public static void scanFile(File f) throws IOException{
		scanText(getFullFileText(f));
	}

	/** Scans a string.  Any output is currently written to the console.
	 * The general procedure of this method is as follows:
	 * <ol>
	 * <li> Create a map of all of the words and their frequencies in the given string
	 * <li> Create a map of all matching words and their frequencies in the COCA Excel sheet
	 * <li> Merge the data of the two maps by creating a list of {@link Word} objects that contains values for both the frequencies
	 * <li> From within the Word class, calculate the <i>normalized</i> frequency by comparing the sample frequency with the COCA frequency
	 * <li> Print the results
	 * </ol>
	 * @param text The text to scan
	 * @throws IOException 
	 */
	public static void scanText(String text) throws IOException{


		/** Map of all of the words in the provided string (key) and the number of occurrences (value) */
		TreeMap<String, Integer> map;
		/** Map of all of the words in the COCA Academic texts (key) and the number of occurrences (value) */
		TreeMap<String, Integer> cocaMap;

		System.out.println("Beginning scan...");


		// Clean the array of words and load the words into the map
		map = getWordCount(text);

		// Get all entries into a set
		Set<Map.Entry<String, Integer>> entrySet = map.entrySet();

		// Create word list
		List<Word> wordlist = new ArrayList<Word>(entrySet.size());

		// Populate the COCA map using words from the sample
		cocaMap = getCocaMapFromWords(map);

		// Get key and value from each entry
		for (Map.Entry<String, Integer> entry: entrySet){

			// Ignore really infrequent words
			if (entry.getValue() > 4){
				try{
					wordlist.add(new Word(entry.getKey(), entry.getValue(), cocaMap.get(entry.getKey())));
				} catch (Exception e){} // Do nothing if an error occurs
			}
		}

		/** Sorts the wordlist based on the implementation of the compareTo() method in the Word class.
		 * Note: This method requires Java 8 or higher */ 
		wordlist.sort(null); // TODO look into NavigableMap and NavigableSet for this

		// Print
		//		System.out.printf("Occurrence of top %d words in %s:\n", wordlist.size(), f.getName());
		System.out.printf("Occurrence of %d words in the sample:\n", wordlist.size());
		System.out.println("Raw freq\tCOCA freq\tNorm freq\tWord");
		System.out.println("----------------------------------------------------");

		for (Word w : wordlist){
			System.out.printf("%d\t\t%d\t\t%.2f\t\t%s\n", w.getRawFrequency(), w.getGlobalFrequency(), w.getNormalFreq(),  w.getValue());
		}

		System.out.println("----------------------------------------------------");
	}

	/**
	 * @param map A map containing the words for which to search
	 * @return A TreeMap of all of the words in the COCA sheet (key) and the occurrence of each word (value)
	 * @throws IOException
	 */
	public static TreeMap<String, Integer> getCocaMapFromWords(TreeMap<String, Integer> map) throws IOException{
		
		/** The column in the sheet containing the words */
		final int COL_WORD = 3;		
		/** The column in the sheet containing the parts of speech (PoS) */
		final int COL_POS = 4;		
		/** The column in the sheet containing the word count in all COCA entries */
		final int COL_COCA_ALL = 5;
		/** The column in the sheet containing the word count in all COCA Academic entries */
		final int COL_COCA_ACAD = 6;

		/** Map of all of the words in the COCA Academic texts (key) and the number of occurrences (value) */
		TreeMap<String, Integer> cocaMap = new TreeMap<String, Integer>();

		// POI jazz to get the first sheet from the Excel file
		POIFSFileSystem fs = new POIFSFileSystem(new FileInputStream(COCA_FILE));
		HSSFWorkbook wb = new HSSFWorkbook(fs);
		HSSFSheet sheet = wb.getSheetAt(0);
		HSSFRow row;

		// Get the number of rows in the sheet
		int rows = sheet.getPhysicalNumberOfRows();

		int cols = 0; // Number of columns
		int tmp = 0;

		// This trick ensures that we get the data properly even if it doesn't start from first few rows
		for(int i = 0; i < 10 || i < rows; i++) {
			row = sheet.getRow(i);
			if(row != null) {
				tmp = sheet.getRow(i).getPhysicalNumberOfCells();
				if(tmp > cols) cols = tmp;
			}
		}

		for(int r = 1; r < rows; r++) {
			row = sheet.getRow(r);
			if(row != null) {
				// Get the word from the row
				String word = row.getCell(COL_WORD).getStringCellValue();

				// Get the part of the speech of the word in the list
				String pos = row.getCell(COL_POS).getStringCellValue();
				
				// If the word is in the map, get the freq value and add it to the map
				if (map.containsKey(word) && (
						pos.equals("n")		// Noun
						|| pos.equals("j")	// Adjective
						|| pos.equals("v")	// Verb
						|| pos.equals("r")	// Adverb						
				)){
					cocaMap.put(word, (int)row.getCell(COL_COCA_ALL).getNumericCellValue());
				}

			}
		}

		return cocaMap;

	}


	/**
	 * @param text The string containing the words to count.
	 * @return A TreeMap containing key-value pairs of the words (key) and the number of occurrences (value)
	 */
	public static TreeMap<String, Integer> getWordCount(String text){

		// Split the text into words based on this regex
		String[] words = text.split("[ \n\t\r.,;:!?(){}]");

		// Create the map to store the words
		TreeMap<String, Integer> map = new TreeMap<String, Integer>();

		for (int i = 0; i < words.length; i++) {

			// Tidy up the word slightly
			String key = words[i].toLowerCase().trim();

			if (words[i].length() >= 1 && Character.isLetter(words[i].charAt(0)) ) {

				// If the word doesn't exist in the map, add it
				if (map.get(key) == null) {
					map.put(key, 1);
				}
				// If it does exist, increment the value 
				else {
					int value = map.get(key).intValue();
					value++;
					map.put(key, value);
				}
			}
		}

		return map;
	}

}
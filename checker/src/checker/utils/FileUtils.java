package checker.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;

public class FileUtils {

	public static String readFileToString(String path) {
		String content = "";
		try (BufferedReader br = new BufferedReader(new FileReader(new File(path)))) {
			String line = null;
			while ((line = br.readLine()) != null) {
				content += line + System.lineSeparator();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return content;
	}

	public static void writeStringToFile(String s, String path) {
		try {
			File f = new File(path);
			FileWriter w = new FileWriter(f, false);
			// increase the buffer size since we often need to write big files
			BufferedWriter writer = new BufferedWriter(w, 1048576);
			writer.write(s);
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void appendStringToFile(String s, String path) {
		try {
			File f = new File(path);
			if (!f.exists()) {
				f.createNewFile();
			}
			FileWriter w = new FileWriter(f, true);
			BufferedWriter writer = new BufferedWriter(w, 8192);
			writer.write(s);
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void writeArrayToFile(ArrayList<String> ss, String path) {
		String content = "";
		for (int i = 0; i < ss.size() - 1; i++) {
			content += ss.get(i) + System.lineSeparator();
		}

		if (ss.size() - 1 >= 0) {
			content += ss.get(ss.size() - 1);
		}

		writeStringToFile(content, path);
	}

	public static void copyFileContent(String src, String tgt, boolean append) {
		File f2 = new File(tgt);
		if (!f2.exists()) {
			try {
				f2.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		InputStream fis = null;
		OutputStream fos = null;
		try {
			fis = new BufferedInputStream(new FileInputStream(src));
			fos = new BufferedOutputStream(new FileOutputStream(tgt, true));

			byte[] buf = new byte[8192];

			int i;
			while ((i = fis.read(buf)) != -1) {
				fos.write(buf, 0, i);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static void writeFirstNLinesToFileWithPadding(String src, int size, String tgt) {
		int lNum = countLines(src);
		int times = size / lNum;
		int remains = size % lNum;
		while (times > 0) {
			copyFileContent(src, tgt, true);
			times--;
		}

		writeFirstNLinesToFile(src, remains, tgt);
	}

	public static void writeFirstNLinesToFile(String src, int size, String tgt) {
		File f1 = new File(src);
		File f2 = new File(tgt);
		if (!f2.exists()) {
			try {
				f2.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		String content = "";
		FileWriter w = null;
		try (BufferedReader br = new BufferedReader(new FileReader(f1))) {
			w = new FileWriter(f2, true);
			BufferedWriter writer = new BufferedWriter(w, 8192);

			String line = null;
			int i = 0;
			while ((line = br.readLine()) != null) {
				if (i < size) {
					if (i % 1000 == 0) {
						writer.write(content);
						writer.flush();
						content = "";
					}

					content += line + System.lineSeparator();
					i++;
				} else {
					break;
				}
			}

			writer.write(content);
			writer.flush();
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static int countLines(String path) {
		int count = 0;
		try (BufferedReader br = new BufferedReader(new FileReader(new File(path)))) {
			while (br.readLine() != null) {
				count++;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return count;
	}

	public static void delete(String path) {
		File f = new File(path);
		if (f.exists()) {
			f.delete();
		}
	}

	public static void removeLines(String path, HashSet<String> lines) {
		File file = new File(path);
		File temp = new File(path + ".temp");

		if (temp.exists()) {
			temp.delete();
		}

		try {
			temp.createNewFile();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		BufferedReader reader = null;
		BufferedWriter writer = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			writer = new BufferedWriter(new FileWriter(temp));
			String line;
			while ((line = reader.readLine()) != null) {
				if (lines.contains(line)) {
					continue;
				}

				writer.write(line + System.lineSeparator());
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		temp.renameTo(file);
	}

	public static void removeLinesById(String path, HashSet<String> ids) {
		File file = new File(path);
		File temp = new File(path + ".temp");

		if (temp.exists()) {
			temp.delete();
		}

		try {
			temp.createNewFile();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		BufferedReader reader = null;
		BufferedWriter writer = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			writer = new BufferedWriter(new FileWriter(temp));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("results[")) {
					// the input file is the raw output file
					String id = line.substring(line.indexOf("[") + 1, line.indexOf("][SEQ]"));
					id = id.replaceAll("\\!", " ** ");
					if (ids.contains(id)) {
						continue;
					}
				} else if (line.contains("---")) {
					// the input file is the sequence file
					String id = line.split("---")[0];
					if (ids.contains(id)) {
						continue;
					}
				}

				writer.write(line + System.lineSeparator());
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		temp.renameTo(file);
	}
}

/**
 * 用于进行神经网络模型计算
 */
package org.neofung.hownet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * @author shuaifen
 * 
 */
public class NeuralNetwork {

	private static HowNet mHowNet;
	private static int mDimensionality;

	public NeuralNetwork() {
		mHowNet = new HowNet();
		mDimensionality = mHowNet.getSememesCount();
		getVector("安");
	}

	/**
	 * 获取输入word的义原向量
	 * 
	 * @param word
	 * @return
	 */
	public double[] getVector(String word) {
		double vector[] = new double[mDimensionality];
		List<Pair<String, List<String>>> list = mHowNet.getSemantics(word);
		if (null == list) {
			return null;
		}
		for (Pair<String, List<String>> pair : list) {
			List<String> semantic = pair.getSecond();
			for (String sememe : semantic) {
				int id = mHowNet.getSememeId(sememe);
				vector[id] = 1.0;

				String father = mHowNet.getFather(sememe);
				while (null != father) {

					int father_id = mHowNet.getSememeId(father);
					vector[father_id] = (0.5 * vector[id] > vector[father_id]) ? 0.5 * vector[id]
							: vector[father_id]; // 递归更新权值
					id = father_id;
					father = mHowNet.getFather(father);
				}
			}
		}
		return vector;
	}

	/**
	 * 归一化
	 * 
	 * @param vector
	 * @return
	 */
	public double[] normalize(double[] vector) {
		if (null == vector) {
			return null;
		}
		double[] output = new double[vector.length];
		double sum = 0.0;

		for (int i = 0; i < vector.length; i++) {
			sum += vector[i] * vector[i];
		}
		double z = 1.0 / Math.sqrt(sum);

		for (int i = 0; i < output.length; i++) {
			output[i] = z * vector[i];
		}

		return output;
	}

	public void writeVector(OutputStreamWriter osw, double[] vector, char split)
			throws IOException {
		for (double d : vector) {
			osw.write(String.valueOf(d) + split);
		}
	}

	/**
	 * 将数据data分为输入集和目标集
	 * 
	 * @param dataPath
	 * @param inputPath
	 * @param targetPath
	 */
	private void translate(String dataPath, String inputPath, String targetPath) {
		File dataFile = new File(dataPath);
		File inputFile = new File(inputPath);
		File targetFile = new File(targetPath);

		BufferedReader reader = null;
		FileOutputStream inputFileOutputStream = null;
		FileOutputStream targetFileOutputStream = null;
		OutputStreamWriter inputOutputStreamWriter = null;
		OutputStreamWriter targetOutputStreamWriter = null;

		try {

			if (!inputFile.exists()) {
				inputFile.createNewFile();
			}
			if (!targetFile.exists()) {
				targetFile.createNewFile();
			}

			inputFileOutputStream = new FileOutputStream(inputPath);
			inputOutputStreamWriter = new OutputStreamWriter(
					inputFileOutputStream);
			targetFileOutputStream = new FileOutputStream(targetFile);
			targetOutputStreamWriter = new OutputStreamWriter(
					targetFileOutputStream);
			reader = new BufferedReader(new FileReader(dataFile));

			String line;

			while ((line = reader.readLine()) != null && line.length() > 1) {
				String[] words = line.split("\\s+");
				double[] vector_a = normalize(getVector(words[0]));
				double[] vector_b = normalize(getVector(words[1]));

				if (null == vector_a || null == vector_b) {
					if (null == vector_a) {
						System.out.println(words[0]);
					}
					if (null == vector_b) {
						System.out.println(words[1]);
					}
					continue;
				}

				System.out.println(vector_a.length + " " + vector_b.length);

				writeVector(inputOutputStreamWriter, vector_a, ' ');
				writeVector(inputOutputStreamWriter, vector_b, ' ');
				inputOutputStreamWriter.write('\n');

				targetOutputStreamWriter.write(words[2]);
				targetOutputStreamWriter.write("\n");
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (null != inputOutputStreamWriter) {
				try {
					inputOutputStreamWriter.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (null != inputFileOutputStream) {
				try {
					inputFileOutputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (null != targetOutputStreamWriter) {
				try {
					targetOutputStreamWriter.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (null != targetFileOutputStream) {
				try {
					targetFileOutputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (null != reader) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

	public void translate(String dataname, String split, int count) {
		for (int i = 0; i < count; i++) {
			String dataPath = dataname + i;
			String inputPath = "input" + split + dataPath;
			String targetPath = "target" + split + dataPath;
			translate(dataPath, inputPath, targetPath);
		}
	}

	/**
	 * 获取total个[0,max)之间的随机数
	 * 
	 * @param total
	 * @param max
	 * @return
	 */
	public HashSet<Integer> getRandom(int total, int max) {
		HashSet<Integer> set = new HashSet<Integer>();
		int count = 0;
		Random random = new Random(System.currentTimeMillis());
		while (set.size() < total) {
			Integer integer = Math.abs(random.nextInt()) % max;
			set.add(integer);
		}
		return set;
	}

	/**
	 * 将输入数据分成train和test两部分
	 * 
	 * @param inputPath
	 *            输入数据的地址
	 * @param trainDataName
	 *            训练数据的前缀
	 * @param testDataName
	 *            测试数据的前缀
	 * @param flag
	 *            输出文件中前缀和组数之间的符号
	 * @param rate
	 *            测试数据占输入数据的比率
	 */
	public void devideInputData(String inputPath, String trainDataName,
			String testDataName, String flag, double rate, int outputFileCount) {
		File file = new File(inputPath);
		FileOutputStream trainFileOutputStream = null;
		OutputStreamWriter trainOutputStreamWriter = null;
		FileOutputStream testFileOutputStream = null;
		OutputStreamWriter testOutputStreamWriter = null;
		BufferedReader reader = null;
		ArrayList<String> arrayList = new ArrayList<String>();
		try {
			reader = new BufferedReader(new FileReader(file));
			String line;
			while ((line = reader.readLine()) != null) {
				arrayList.add(line);
			}

			int count = arrayList.size();

			for (int i = 0; i < outputFileCount; i++) {
				Set set = getRandom((int) (count * rate), count);
				File trainFile = new File(trainDataName + flag + i);
				if (!trainFile.exists()) {
					trainFile.createNewFile();
				}
				File testFile = new File(testDataName + flag + i);
				if (!testFile.exists()) {
					testFile.createNewFile();
				}

				trainFileOutputStream = new FileOutputStream(trainFile);
				testFileOutputStream = new FileOutputStream(testFile);
				trainOutputStreamWriter = new OutputStreamWriter(
						trainFileOutputStream);
				testOutputStreamWriter = new OutputStreamWriter(
						testFileOutputStream);

				for (int j = 0; j < arrayList.size(); j++) {
					if (set.contains(Integer.valueOf(j))) {
						testOutputStreamWriter.write(arrayList.get(j) + "\n");
					} else {
						trainOutputStreamWriter.write(arrayList.get(j) + "\n");
					}
				}

				trainOutputStreamWriter.close();
				trainFileOutputStream.close();
				testOutputStreamWriter.close();
				testFileOutputStream.close();
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		NeuralNetwork neuralNetwork = new NeuralNetwork();
		// neuralNetwork.run("input.txt", "output.txt");
		neuralNetwork.devideInputData("input.txt", "train", "test", "-", 0.2,
				10);
		neuralNetwork.translate("train-", "-", 10);

	}

}

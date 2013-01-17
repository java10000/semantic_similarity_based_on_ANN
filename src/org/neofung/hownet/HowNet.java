/**
 * 用于在知网中读取分析义原信息
 */
package org.neofung.hownet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * @author shuaifen
 * 
 */
public class HowNet {

	// mSememesMap保存的是每个义原的ID号
	private HashMap<String, Integer> mSememesMap;

	// mSememesFather保存的是每个义原的上位义是谁
	private HashMap<String, String> mSememesFather;

	// mGlossary保存的是glossary.dat的数据, MultiHashMap 运行一个key值保存多个value,
	// Pair中的First保存的是key的词性, second保存的是key的义项
	private MultiHashMap<String, List<Pair<String, List<String>>>> mGlossary;

	// mWhole是从WHOLD.DAT中获取的数据，是一个单纯的数组
	private ArrayList<Pair<String, Integer>> mWhole;

	public HowNet() {
		init(false);
	}

	/**
	 * 检测mSememesMap中全部义原的value是否超过了mSememesMap的大小
	 * 
	 * @return
	 */
	public boolean checkSememesMapSum() {
		Integer count = mSememesMap.size();
		for (Entry<String, Integer> entry : mSememesMap.entrySet()) {
			if (entry.getValue() >= count) {
				System.out.println(entry);
				return false;
			}
		}
		return true;
	}

	private void init(boolean rebuild) {
		if (rebuild) {
			readWHOLE();
			readGlossary();
			getSememesCount();
			sortFather();
			saveData();
		} else {
			try {
				loadData();
			} catch (Exception e) {
				// 假如序列化读入异常，则重新读取
				readWHOLE();
				readGlossary();
				getSememesCount();
				sortFather();
			}
		}
	}

	/**
	 * 整理全部义原的上位义
	 */
	private void sortFather() {
		mSememesFather = new HashMap<String, String>();
		for (int i = 0; i < mWhole.size(); i++) {
			Pair<String, Integer> pair = mWhole.get(i);
			int fatherIdx = pair.getSecond().intValue();
			if (fatherIdx == i) {
				mSememesFather.put(pair.getFirst(), null);
			} else {
				Pair<String, Integer> father = mWhole.get(fatherIdx);
				mSememesFather.put(pair.getFirst(), father.getFirst());
			}
		}
	}

	/**
	 * 整理带符号的义原，如果某个义原带有符号，则它的上位义也带有符号
	 */
	private void sortSememes() {
		System.out.println(mSememesMap.size());
		System.out.println(mGlossary.size());
		System.out.println(mSememesFather.size());
		System.out.println(mWhole.size());

		for (Entry<String, List<Pair<String, List<String>>>> entry : mGlossary
				.entrySet()) {
			List<Pair<String, List<String>>> list = entry.getValue();
			for (Pair<String, List<String>> pair : list) {
				List<String> semantic = pair.getSecond();
				for (String sememe : semantic) {
					if (!Character.isLetter(sememe.charAt(0))) {
						if (!sememe.matches("[\\(\\{].*")) {
							char sign = sememe.charAt(0);
							sememe = sememe.substring(1);
							while ((sememe = getFather(sememe)) != null) {
								// System.out.println(sign + sememe);
								putSememe(sign + sememe);
							}
						}
					}
				}
			}
		}

		saveData();
		System.out.println(mSememesMap.size());
		System.out.println(mGlossary.size());
		System.out.println(mSememesFather.size());
		System.out.println(mWhole.size());
	}

	/**
	 * 获取义原的上位义
	 * 
	 * @param sememe
	 * @return
	 */
	public String getFather(String sememe) {
		return mSememesFather.get(sememe);
	}

	/**
	 * 读取WHOLE.DAT的数据
	 * 
	 * @return
	 */
	private int readWHOLE() {
		if (null != mWhole)
			return mWhole.size();

		mWhole = new ArrayList<Pair<String, Integer>>();

		File file = new File("WHOLE.DAT");
		BufferedReader reader = null;

		try {
			reader = new BufferedReader(new FileReader(file));
			String line = null;
			while ((line = reader.readLine()) != null) {
				Pair<String, Integer> pair = new Pair<String, Integer>();

				String subs[] = line.split("\\s+");
				pair.setFirst(subs[2]);
				pair.setSecond(Integer.valueOf(subs[3]));

				mWhole.add(pair);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return mWhole.size();
	}

	/**
	 * 读取glossary.dat的数据
	 * 
	 * @return
	 */
	private int readGlossary() {
		if (null != mGlossary)
			return mGlossary.size();

		mGlossary = new MultiHashMap<String, List<Pair<String, List<String>>>>();

		File file = new File("glossary.dat");
		BufferedReader reader = null;

		try {
			reader = new BufferedReader(new FileReader(file));
			String line = null;
			while ((line = reader.readLine()) != null) {
				String subs[] = line.split("\\s*\\t");
				Pair<String, List<String>> pair = new Pair<String, List<String>>();
				pair.setFirst(subs[1]); // 词性

				String[] semantic = subs[2].split(",");
				ArrayList<String> sememeList = new ArrayList<String>();
				for (String string : semantic) {
					sememeList.add(string);
				}
				pair.setSecond(sememeList); // 义项

				@SuppressWarnings("unchecked")
				ArrayList<Pair<String, List<String>>> arrayList = (ArrayList<Pair<String, List<String>>>) mGlossary
						.get(subs[0]);
				if (null == arrayList) {
					arrayList = new ArrayList<Pair<String, List<String>>>();
				}
				arrayList.add(pair);
				mGlossary.put(subs[0], arrayList);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return mGlossary.size();
	}

	/**
	 * 计算总共有多少种不同的义原, 并将它们全部加入mSememesMap中, 其中mSememesMap的value是一个数值,
	 * 指示义原在向量中的位置。
	 * 
	 * @return
	 */
	public int getSememesCount() {
		if (null == mSememesMap) {
			mSememesMap = new HashMap<String, Integer>();
			readGlossary();
			readWHOLE();
		} else {
			return mSememesMap.size();
		}

		// 处理glossary中的不同义原
		if (null != mGlossary) {
			for (Entry<String, List<Pair<String, List<String>>>> entry : mGlossary
					.entrySet()) {
				List<Pair<String, List<String>>> semantics = entry.getValue();
				for (Pair<String, List<String>> pair : semantics) {
					List<String> semantic = pair.getSecond();
					for (String seme : semantic) {
						putSememe(seme);
					}
				}
			}
		}
		// 处理WHOLE.DAT中的不同的义原
		for (Iterator<Pair<String, Integer>> iterator = mWhole.iterator(); iterator
				.hasNext();) {
			Pair<String, Integer> pair = iterator.next();
			putSememe(pair.getFirst());

		}
		return mSememesMap.size();
	}

	/**
	 * 获取词语word对应的全部义项。
	 * 
	 * @param word
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public List<Pair<String, List<String>>> getSemantics(String word) {
		if (null == mGlossary || !mGlossary.containsKey(word)) {
			return null;
		}
		return (List<Pair<String, List<String>>>) mGlossary.get(word);
	}

	/**
	 * 是否包含word这个词
	 * 
	 * @param word
	 * @return
	 */
	public boolean containsWord(String word) {
		return mGlossary.containsKey(word);
	}

	/**
	 * 获得每个义原的ID号
	 * 
	 * @param sememe
	 * @return
	 */
	public int getSememeId(String sememe) {
		Integer integer = mSememesMap.get(sememe);
		if (null == integer) {
			return -1;
		} else {
			return integer.intValue();
		}
	}

	/**
	 * 往mSememesMap中插入义原
	 * 
	 * @param sememe
	 * @return
	 */
	public int putSememe(String sememe) {
		if (!mSememesMap.containsKey(sememe)) {
			int count = mSememesMap.size();
			mSememesMap.put(sememe, Integer.valueOf(count));
			return count;
		} else {
			return mSememesMap.get(sememe).intValue();
		}
	}

	/**
	 * 保存各个类变量数据
	 */
	public void saveData() {
		try {
			ObjectOutputStream outputStream = new ObjectOutputStream(
					new FileOutputStream("neo_glossary"));
			outputStream.writeObject(mGlossary);

			outputStream = new ObjectOutputStream(new FileOutputStream(
					"neo_whole"));
			outputStream.writeObject(mWhole);

			outputStream = new ObjectOutputStream(new FileOutputStream(
					"neo_sememes"));
			outputStream.writeObject(mSememesMap);

			outputStream = new ObjectOutputStream(new FileOutputStream(
					"neo_father"));
			outputStream.writeObject(mSememesFather);

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 读取各个类变量数据
	 * 
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	@SuppressWarnings("unchecked")
	private void loadData() throws FileNotFoundException, IOException,
			ClassNotFoundException {

		ObjectInputStream inputStream = new ObjectInputStream(
				new FileInputStream("neo_glossary"));
		mGlossary = (MultiHashMap<String, List<Pair<String, List<String>>>>) inputStream
				.readObject();

		inputStream = new ObjectInputStream(new FileInputStream("neo_whole"));
		mWhole = (ArrayList<Pair<String, Integer>>) inputStream.readObject();

		inputStream = new ObjectInputStream(new FileInputStream("neo_sememes"));
		mSememesMap = (HashMap<String, Integer>) inputStream.readObject();

		inputStream = new ObjectInputStream(new FileInputStream("neo_father"));
		mSememesFather = (HashMap<String, String>) inputStream.readObject();
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		HowNet data = new HowNet();
		System.exit(0);
	}

}

class MultiHashMap<K extends Object, V extends List> extends AbstractMap
		implements Serializable {
	private static final long serialVersionUID = 1041712953752728541L;

	public MultiHashMap() {
		iMap = new HashMap<K, V>();
	}

	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		return iMap.entrySet();
	}

	@Override
	public Object put(Object k, Object v) {
		boolean isList = v instanceof List;
		if (isList || (null == v)) {
			return iMap.put(k, v);
		}
		return null;
	}

	public Object put(K k, V v) {
		return iMap.put(k, v);
	}

	@Override
	public void putAll(Map m) {

		if (m instanceof MultiHashMap) {

			Iterator itr = m.entrySet().iterator();
			Entry entry = null;
			while (itr.hasNext()) {
				entry = (Entry) itr.next();
				put(entry.getKey(), entry.getValue());
			}
		}
	}

	private final Map iMap;

}

class Pair<K, V> implements Serializable {
	private static final long serialVersionUID = 1041712221752728541L;
	private K first;
	private V second;

	public K getFirst() {
		return first;
	}

	public void setFirst(K val) {
		first = val;
	}

	public V getSecond() {
		return second;
	}

	public void setSecond(V val) {
		second = val;
	}

	@Override
	public String toString() {
		return first + ":" + second;
	}
}

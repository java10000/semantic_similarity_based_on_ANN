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

	// mGlossary保存的是glossary.dat的数据. MultiHashMap
	// 运行一个key值保存多个value. mGlossary中的key记录的是词语, 而value是个List记录的是这个词语的全部义项.
	// 在List中Pair的First保存的是词语这个义项的词性, second保存的是这个义项的义原集合
	private MultiHashMap<String, List<Pair<String, List<String>>>> mGlossary;

	// mWhole是从WHOLD.DAT中获取的数据, 是一个单纯的数组.
	// 数组的元素是Pair, Pair中的first是义原, 而second是这个义原的上位义在数组中的位置
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

	/**
	 * 初始化
	 * 
	 * @param rebuild
	 *            是否重新读取原始数据并重新构造过去保存的内部数据
	 */
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
				// 假如序列化读入异常, 则重新读取
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
	 * 整理带符号的义原, 如果某个义原带有符号, 则它的上位义也带有符号
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
	 *            义原的名字
	 * @return 义原的上位义, 如果没有则返回null
	 */
	public String getFather(String sememe) {
		return mSememesFather.get(sememe);
	}

	/**
	 * 读取WHOLE.DAT的数据, WHOLE.DAT中保存的是全部基本义原
	 * 
	 * @return 全部基本义原的数量
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
	 * 读取glossary.dat的数据, glossary.dat的每一条记录保存的是一个义项, 义项的词性和义项所对应的义原
	 * 
	 * @return glossary.dat中全部不同的词语
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
	 * 指示义原在向量中的位置.
	 * 
	 * @return 全部义原的数目, 包括基本义原和关系义原
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
	 * 获取词语word对应的全部义项.
	 * 
	 * @param word
	 *            词语
	 * @return 一个保护这个词语全部义项的列表, 如果没有则返回null
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
	 *            词语
	 * @return 数据中包含, 则返回true, 否则返回false
	 */
	public boolean containsWord(String word) {
		return mGlossary.containsKey(word);
	}

	/**
	 * 获得每个义原的ID号
	 * 
	 * @param sememe
	 *            义原
	 * @return 如果义原存在, 返回义原的ID号, 否则返回-1
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
	 *            义原
	 * @return 插入后的义原的ID号, 如果本身就已经存在则不插入切返回已有的ID号
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

/**
 * MultiHashMap 用于保存一个key对应多个value的关系的HashMap
 * 
 * @author neo
 * @version 2012-12-14
 * 
 * @param <K>
 *            Key.
 * @param <V>
 *            The List of value.
 */
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

	@Override
	public String toString() {
		return "MultiHashMap [iMap=" + iMap + "]";
	}

}

/**
 * Pair 类用于表示一个first和second的对组合, 是一个可序列化的类
 * 
 * @author neo
 * @version 2012-12-15
 * 
 * @param <K>
 *            First
 * @param <V>
 *            Second
 */
class Pair<K, V> implements Serializable {
	private static final long serialVersionUID = 1041712221752728541L;
	private K first;
	private V second;

	/**
	 * 获取first
	 * 
	 * @return
	 */
	public K getFirst() {
		return first;
	}

	/**
	 * 设置second
	 * 
	 * @param val
	 */
	public void setFirst(K val) {
		first = val;
	}

	/**
	 * 获取second
	 * 
	 * @return
	 */
	public V getSecond() {
		return second;
	}

	/**
	 * 设置second
	 * 
	 * @param val
	 */
	public void setSecond(V val) {
		second = val;
	}

	@Override
	public String toString() {
		return first + ":" + second;
	}
}

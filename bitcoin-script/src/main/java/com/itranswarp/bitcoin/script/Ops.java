package com.itranswarp.bitcoin.script;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.reflect.ClassPath;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.itranswarp.bitcoin.BitcoinException;
import com.itranswarp.bitcoin.util.ClasspathUtils;

/**
 * Script ops: https://en.bitcoin.it/wiki/Script
 * 
 * @author liaoxuefeng
 */
public class Ops {

	final Log Log = LogFactory.getLog(getClass());

	public static Op getOp(Integer code) {
		return OPS.get(code);
	}

	// holds all ops:
	static final Map<Integer, Op> OPS = scanOps();

	static Map<Integer, Op> scanOps() {
		Map<Integer, Op> map = new HashMap<>();
		try {
			for (Class<?> clazz : getClasses(Ops.class.getPackage().getName() + ".op")) {
				Op op = (Op) clazz.newInstance();
				map.put(op.code, op);
			}
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new BitcoinException(e);
		}
		return map;
	}

	public static List<Class<?>> getClasses(String packageName) throws IOException {
		return ClassPath.from(ClasspathUtils.class.getClassLoader()).getTopLevelClasses(packageName).stream()
				.map((info) -> info.load()).collect(Collectors.toList());
	}
}

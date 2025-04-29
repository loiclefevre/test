/*
 ** Oracle Test Pilot
 **
 ** Copyright (c) 2025 Oracle
 ** Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl/
 */
package com.oracle.testpilot.json;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class JSON<T> {
	private Class<T> clazz;
	private Constructor<T> constructor;

	public JSON(Class<T> clazz) {
		this.clazz = clazz;
		try {
			this.constructor = clazz.getConstructor();
		}
		catch (NoSuchMethodException nsme) {
			throw new RuntimeException("Empty constructor not found!", nsme);
		}
	}

	public T parse(final String json) {
		try {
			Map<Object, Object> parsed = (Map<Object, Object>) parseJSON(json);

			return (T) filledValues(parsed, clazz, constructor);
		}
		catch (Exception e) {
			return null;
		}
	}

	private Object filledValues(Map<Object, Object> parsed, Class<?> clazz, Constructor constructor) throws InvocationTargetException, InstantiationException, IllegalAccessException {
		final Object ret = constructor.newInstance();

		for (Map.Entry<Object, Object> e : parsed.entrySet()) {
			try {
				final String fieldName = String.valueOf(e.getKey());
				final Field f = clazz.getDeclaredField(fieldName);
				//System.out.println("Field found: " + e.getKey() + " -> " + e.getValue());
				if (f.getType().isArray()) {
					final List<Object> elements = (List<Object>) e.getValue();
					//System.out.println("items: "+elements.size());
					final Class<?> c = f.getType().arrayType();
					final Class<?> componentType = f.getType().componentType();
					final Object array = Array.newInstance(componentType, elements.size());
					int nb = 0;
					for (Object o : (List<Object>) e.getValue()) {
						if (o instanceof Map) {
							try {
								//System.out.println("Array component type is: "+componentType);
								Array.set(array, nb++, componentType.cast(filledValues((Map<Object, Object>) o, componentType, componentType.getConstructor())));
							}
							catch (NoSuchMethodException ex) {
								throw new RuntimeException(ex);
							}
						}
						else {
							Array.set(array, nb++, f.getType().cast(o));
						}
					}

					// direct assignment
					try {
						Method setter = clazz.getDeclaredMethod("set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1), f.getType());
						setter.invoke(ret, f.getType().cast(array));
					}
					catch (NoSuchMethodException ignored) {
					}
					catch (InvocationTargetException ex) {
						throw new RuntimeException(ex);
					}
					catch (IllegalAccessException ex) {
						throw new RuntimeException(ex);
					}

				}
				else {
					// direct assignment
					try {
						Method setter = clazz.getDeclaredMethod("set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1), f.getType());
						setter.invoke(ret, e.getValue());
					}
					catch (NoSuchMethodException ignored) {
					}
					catch (InvocationTargetException ex) {
						throw new RuntimeException(ex);
					}
					catch (IllegalAccessException ex) {
						throw new RuntimeException(ex);
					}
				}
			}
			catch (NoSuchFieldException ignored) {
			}
		}

		return ret;
	}

	public static class JSONParseException extends Exception {
		public JSONParseException(String cause) {
			super(cause);
		}
	}

	public static Object parseJSONFile(String file) throws JSONParseException, IOException {
		return parseJSON(Paths.get(file));
	}

	public static Object parseJSON(String text) throws JSONParseException {
		return parseJSON(new Scanner(text));
	}

	public static Object parseJSON(Path file) throws JSONParseException, IOException {
		return parseJSON(new Scanner(file));
	}

	public static Object parseJSON(Scanner s) throws JSONParseException {
		Object ret = null;
		skipWhitespace(s);
		if (s.findWithinHorizon("\\{", 1) != null) {
			final Map<Object, Object> retMap = new HashMap<>();
			ret = retMap;
			skipWhitespace(s);
			if (s.findWithinHorizon("\\}", 1) == null) {
				while (s.hasNext()) {
					Object key = parseJSON(s);
					skipWhitespace(s);
					if (s.findWithinHorizon(":", 1) == null) {
						fail(s, ":");
					}
					Object value = parseJSON(s);
					retMap.put(key, value);
					skipWhitespace(s);
					if (s.findWithinHorizon(",", 1) == null) {
						break;
					}
				}
				if (s.findWithinHorizon("\\}", 1) == null) {
					fail(s, "}");
				}
			}
		}
		else if (s.findWithinHorizon("\"", 1) != null) {
			final StringBuilder sb = new StringBuilder();
			String item;
			boolean endsWithBackSlash;
			do { item = s.findWithinHorizon("[^\"]*",0);
				if(item.isEmpty()) break;
				endsWithBackSlash = item.endsWith("\\");
				sb.append(item);
				if(endsWithBackSlash) {
					sb.append('\"');
					s.findWithinHorizon("\"", 0);
				}
			} while (true);

			//System.out.println("Found string: "+sb.toString());

			ret = sb.toString()
					.replace("\\\\", "\\")
					.replace("\\\"", "\"")
					.replace("\\n", "\n");

			if (s.findWithinHorizon("\"", 1) == null) {
				fail(s, "quote");
			}
		}
		else if (s.findWithinHorizon("'", 1) != null) {
			ret = s.findWithinHorizon("(\\\\\\\\|\\\\'|[^'])*", 0);
			if (s.findWithinHorizon("'", 1) == null) {
				fail(s, "quote");
			}
		}
		else if (s.findWithinHorizon("\\[", 1) != null) {
			ArrayList<Object> retList = new ArrayList<>();
			ret = retList;
			skipWhitespace(s);
			if (s.findWithinHorizon("\\]", 1) == null) {
				while (s.hasNext()) {
					retList.add(parseJSON(s));
					skipWhitespace(s);
					if (s.findWithinHorizon(",", 1) == null) {
						break;
					}
				}
				if (s.findWithinHorizon("\\]", 1) == null) {
					fail(s, ", or ]");
				}
			}
		}
		else if (s.findWithinHorizon("true", 4) != null) {
			ret = true;
		}
		else if (s.findWithinHorizon("false", 5) != null) {
			ret = false;
		}
		else if (s.findWithinHorizon("null", 4) != null) {
			ret = null;
		}
		else {
			String numberStart = s.findWithinHorizon("[-0-9+eE]", 1);
			if (numberStart != null) {
				String numStr = numberStart + s.findWithinHorizon("[-0-9+eE.]*", 0);
				if (numStr.contains(".") | numStr.contains("e")) {
					ret = Double.valueOf(numStr);
				}
				else {
					ret = Long.valueOf(numStr);
				}
			}
			else {
				throw new JSONParseException("No JSON value found. Found: " + s.findWithinHorizon(".{0,5}", 5));
			}
		}
		return ret;
	}

	private static void fail(Scanner scanner, String expected) throws JSONParseException {
		throw new JSONParseException("Expected " + expected + " but found:" + scanner.findWithinHorizon(".{0,5}", 5));
	}

	private static void skipWhitespace(Scanner s) {
		s.findWithinHorizon("\\s*", 0);
	}
}

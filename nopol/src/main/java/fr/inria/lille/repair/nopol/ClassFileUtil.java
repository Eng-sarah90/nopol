package fr.inria.lille.repair.nopol;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.StringTokenizer;

import org.eclipse.jdt.internal.compiler.util.Messages;

/**
 * Taken From Spoon-Class loader project
 * 
 *
 */
public class ClassFileUtil {

	static int u2(byte[] bytes, int offset) {
		int i = 0;
		i |= bytes[offset] & 0xFF;
		i <<= 8;
		i |= bytes[offset + 1] & 0xFF;
		return i;
	}

	static long u4(byte[] bytes, int offset) {
		long l = 0;
		l |= bytes[offset] & 0xFF;
		l <<= 8;
		l |= bytes[offset + 1] & 0xFF;
		l <<= 8;
		l |= bytes[offset + 2] & 0xFF;
		l <<= 8;
		l |= bytes[offset + 3] & 0xFF;
		return l;
	}

	public static void printBytes(byte[] bytes, int offset) {
		for (int i = offset; i < bytes.length; i++) {
			System.out.print("(" + i + "):" + bytes[i]);
		}
		System.out.println();
	}

	public static void adjustLineNumbers(byte[] bytes, int methodsOffset,
			Map<Integer, Integer> lineNumberMapping) {
		if (lineNumberMapping == null) {
			return;
		}
		int offset = methodsOffset + 2;
		int methodCount = u2(bytes, methodsOffset);
		for (int i = 0; i < methodCount; i++) {
			offset = adjustMethod(bytes, offset, lineNumberMapping);
		}
	}

	static int adjustMethod(byte[] bytes, int offset,
			Map<Integer, Integer> lineNumberMapping) {
		int attrCount = u2(bytes, offset + 6);
		offset += 8;
		for (int i = 0; i < attrCount; i++) {
			offset = adjustCodeAttribute(bytes, offset, lineNumberMapping);
		}
		return offset;
	}

	static int adjustCodeAttribute(byte[] bytes, int offset,
			Map<Integer, Integer> lineNumberMapping) {
		String attrName = getPoolString(bytes, u2(bytes, offset));
		if ("Code".equals(attrName)) {
			int offset2 = offset + 14 + (int) u4(bytes, offset + 10);
			offset2 += u2(bytes, offset2) * 8;
			offset2 += 2;
			int attrCount = u2(bytes, offset2);
			offset2 += 2;
			for (int i = 0; i < attrCount; i++) {
				offset2 = adjustLineNumberAttribute(bytes, offset2,
						lineNumberMapping);
			}
		}
		return offset + 6 + (int) u4(bytes, offset + 2);
	}

	static int adjustLineNumberAttribute(byte[] bytes, int offset,
			Map<Integer, Integer> lineNumberMapping) {
		String attrName = getPoolString(bytes, u2(bytes, offset));
		if ("LineNumberTable".equals(attrName)) {
			int offset2 = offset + 6;
			int lineCount = u2(bytes, offset2);
			offset2 += 2;
			for (int i = 0; i < lineCount; i++) {
				int org = u2(bytes, offset2 + 2);
				Integer pos = lineNumberMapping.get(org);
				if (pos != null) {
					int ipos = pos;
					bytes[offset2 + 2] = (byte) (ipos >> 8);
					bytes[offset2 + 3] = (byte) (ipos);
				} 
				offset2 += 4;
			}
		}
		return offset + 6 + (int) u4(bytes, offset + 2);
	}


	static String getPoolString(byte[] bytes, int index) {
		int offset = 10;
		int i = index;
		while (i > 1) {
			if ((bytes[offset] == ConstantType.Double)
					|| (bytes[offset] == ConstantType.Long)) {
				i--;
			}
			offset = skipPoolConstant(bytes, offset);
			i--;
		}
		if (bytes[offset] != ConstantType.Utf8) {
			throw new RuntimeException(
					"error in pool constant: unable to get utf8 at " + index
							+ " (o=" + offset + ")");
		}
		int length = u2(bytes, offset + 1);
		byte[] string = new byte[length];
		try {
			System.arraycopy(bytes, offset + 3, string, 0, length);
		} catch (Exception e) {
			System.err.println("error getting the string: l=" + length + ", o="
					+ offset);
			printBytes(bytes, offset + 3);
		}
		return new String(string);
	}

	static int skipPoolConstant(byte[] bytes, int offset) {
		byte constantType = bytes[offset];
		switch (constantType) {
		case ConstantType.String:
		case ConstantType.Class:
			return offset + 3;
		case ConstantType.Methodref:
		case ConstantType.InterfaceMethodref:
		case ConstantType.Fieldref:
		case ConstantType.Integer:
		case ConstantType.Float:
		case ConstantType.NameAndType:
			return offset + 5;
		case ConstantType.Double:
		case ConstantType.Long:
			return offset + 9;
		case ConstantType.Utf8:
			return offset + 3 + u2(bytes, offset + 1);
		default:
			throw new RuntimeException("invalid constant pool");
		}
	}

	public static void writeToDisk(boolean generatePackagesStructure,
			String outputPath, String relativeFileName, byte[] bytes)
			throws IOException {

		BufferedOutputStream output = null;
		if (generatePackagesStructure) {
			output = new BufferedOutputStream(new FileOutputStream(new File(
					buildAllDirectoriesInto(outputPath, relativeFileName))));
		} else {
			String fileName = null;
			char fileSeparatorChar = File.separatorChar;
			String fileSeparator = File.separator;
			outputPath = outputPath.replace('/', fileSeparatorChar);
			int indexOfPackageSeparator = relativeFileName
					.lastIndexOf(fileSeparatorChar);
			if (indexOfPackageSeparator == -1) {
				if (outputPath.endsWith(fileSeparator)) {
					fileName = outputPath + relativeFileName;
				} else {
					fileName = outputPath + fileSeparator + relativeFileName;
				}
			} else {
				int length = relativeFileName.length();
				if (outputPath.endsWith(fileSeparator)) {
					fileName = outputPath
							+ relativeFileName.substring(
									indexOfPackageSeparator + 1, length);
				} else {
					fileName = outputPath
							+ fileSeparator
							+ relativeFileName.substring(
									indexOfPackageSeparator + 1, length);
				}
			}
			output = new BufferedOutputStream(new FileOutputStream(new File(
					fileName)));
		}
		try {
			output.write(bytes, 0, bytes.length);
		} finally {
			output.flush();
			output.close();
		}
	}

	static String buildAllDirectoriesInto(String outputPath,
			String relativeFileName) throws IOException {
		char fileSeparatorChar = File.separatorChar;
		String fileSeparator = File.separator;
		File f;
		outputPath = outputPath.replace('/', fileSeparatorChar);
		if (outputPath.endsWith(fileSeparator)) {
			outputPath = outputPath.substring(0, outputPath.length() - 1);
		}
		f = new File(outputPath);
		if (f.exists()) {
			if (!f.isDirectory()) {
				final String message = Messages.bind(Messages.output_isFile, f
						.getAbsolutePath());
				throw new IOException(message);
			}
		} else {
			if (!f.mkdirs()) {
				final String message = Messages.bind(
						Messages.output_notValidAll, f.getAbsolutePath());
				throw new IOException(message);
			}
		}
		StringBuffer outDir = new StringBuffer(outputPath);
		outDir.append(fileSeparator);
		StringTokenizer tokenizer = new StringTokenizer(relativeFileName,
				fileSeparator);
		String token = tokenizer.nextToken();
		while (tokenizer.hasMoreTokens()) {
			f = new File(outDir.append(token).append(fileSeparator).toString());
			
			if ( !f.exists() && !f.mkdir()) {
				throw new IOException(Messages.bind(
						Messages.output_notValid, f.getName()));
			}
			
			token = tokenizer.nextToken();
		}
		return outDir.append(token).toString();
	}

}

class ConstantType {
	public static final byte Class = 7;

	public static final byte Fieldref = 9;

	public static final byte Methodref = 10;

	public static final byte InterfaceMethodref = 11;

	public static final byte String = 8;

	public static final byte Integer = 3;

	public static final byte Float = 4;

	public static final byte Long = 5;

	public static final byte Double = 6;

	public static final byte NameAndType = 12;

	public static final byte Utf8 = 1;
}

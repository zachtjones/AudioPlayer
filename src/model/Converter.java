package model;
/**
 * Converts numerical data types.
 * This is used to convert bytes to their integer equivalents.
 * @author zach jones
 *
 */
public class Converter {

	private static final long third = 256 * 256 * 256;
	private static final long second = 256 * 256;
	
	/**
	 * Returns a unisigned integer stored in a long (uint is not a type in java)
	 * @param bytes The 4 byte array that represents the data.
	 * @return A uint's representation of the little-endian bytes stored in a signed long.
	 * This result will always be >= 0.
	 */
	public static long toUIntLittleEndian(byte[] bytes){
		return toUIntLittleEndian(bytes, 0);
	}
	
	/**
	 * Returns a unisigned integer stored in a long (uint is not a type in java)
	 * @param bytes The byte array that represents the data.
	 * @param offset The starting index in the bytes array to pull 4 bytes from.
	 * @return A uint's representation of the little-endian bytes stored in a signed long.
	 * This result will always be >= 0.
	 */
	public static long toUIntLittleEndian(byte[] bytes, int offset){
		//all bytes have to be treated as unsigned.
		return Byte.toUnsignedLong(bytes[3 + offset]) * third + 
				Byte.toUnsignedLong(bytes[2 + offset]) * second + 
				Byte.toUnsignedLong(bytes[1 + offset]) * 256 + 
				Byte.toUnsignedLong(bytes[0 + offset]);
	}
	
	/**
	 * Returns a unisigned integer stored in a long (uint is not a type in java)
	 * @param bytes The 4 byte array that represents the data.
	 * @return A uint's representation of the big-endian bytes stored in a signed long.
	 * This result will always be >= 0.
	 */
	public static long toUIntBigEndian(byte[] bytes){
		//all bytes have to be treated as unsigned.
				return Byte.toUnsignedLong(bytes[3]) + 
						Byte.toUnsignedLong(bytes[2]) * 256 + 
						Byte.toUnsignedLong(bytes[1]) * second + 
						Byte.toUnsignedLong(bytes[0]) * third;
	}
}

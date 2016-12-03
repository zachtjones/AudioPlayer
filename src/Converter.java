/**
 * Converts numerical data types.
 * 
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
		//all bytes have to be treated as unsigned.
		return Byte.toUnsignedLong(bytes[3]) * third + 
				Byte.toUnsignedLong(bytes[2]) * second + 
				Byte.toUnsignedLong(bytes[1]) * 256 + 
				Byte.toUnsignedLong(bytes[0]);
	}
}

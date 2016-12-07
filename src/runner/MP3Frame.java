package runner;

import java.io.IOException;

public class MP3Frame {

	public enum ChannelMode { 
		/** The more complex channel gets more bits */
		STEREO, 
		/** The Joint Stereo mode considers the redundancy between
		 *  left and right channels to optimize coding. 
		 * There are two styles:
		 * middle/side stereo (MS stereo) and Intensity Stereo.*/
		JOINT_STEREO, 
		/** The channels are encoded independent of each other*/
		DUAL, 
		/** Only one channel is encoded. */
		MONO 
	};

	/** The number of channels of data */
	private ChannelMode numChannels;
	/** true if the frame is padded */
	private boolean isPadded;
	/** Only used if JointStereo is used */
	private boolean intensityStereo, MSStereo;
	/** If true, then use mpeg 1, otherwise mpeg 2*/
	private boolean mpeg_1;
	/** if crc protection is in place */
	private boolean crc;
	/** The number of bits in a sample */
	private int bitsPerSample;
	/** The number of samples per second */
	private int sampleRate;

	/**
	 * Constructs a frame of mp3 audio data.
	 * @param data The byte array that is the contents of the file.
	 * @param offset An int that is the offset from the start of the array.
	 * For example, if offset == 40, then data[40] is the first byte this
	 * will get data from
	 * @throws IOException If there is a format issue with this frame.
	 */
	public MP3Frame(byte[] data, int offset) throws IOException{
		//first 4 bytes are the header
		byte[] header = toBits(data, offset, 4);
		for(int i = 0; i < 11; i++){
			//first 12 bits should be 1
			if(header[i] != 1){
				throw new IOException("Expected header chunk not present");
			}
		}
		
		//check the mpeg version
		this.mpeg_1 = header[12] == 1;

		//check the mpeg layer
		if(header[13] != 0 && header[14] != 1){
			throw new IOException("Improper MPEG Layer, should be layer 3");
		}

		//CRC protection bit. 
		//This is used to set the need to check transmission errors.
		boolean crcProt = header[15] == 1;

		//get the bit rate 
		this.bitsPerSample = getBitRate(header[16], header[17], header[18], header[19]);

		//get the sample rate
		if(this.mpeg_1){
			if(header[20] == 0 && header[21] == 0){
				this.sampleRate = 44100;
			} else if(header[20] == 0 && header[21] == 1){
				this.sampleRate = 48000;
			} else {
				this.sampleRate = 32000;
			}
		} else {
			if(header[20] == 0 && header[21] == 0){
				this.sampleRate = 22050;
			} else if(header[20] == 0 && header[21] == 1){
				this.sampleRate = 24000;
			} else {
				this.sampleRate = 16000;
			}
		}
		
		//get if the chunk is padded
		//this is used to add one byte to the size of this frame
		this.isPadded = (header[22] == 1);

		//private bit, don't care about this value

		//channel is next 2 bits
		if(header[24] == 0 && header[25] == 0){
			this.numChannels = ChannelMode.STEREO;
		} else if(header[24] == 0 && header[25] == 1){
			this.numChannels = ChannelMode.JOINT_STEREO;
		} else if(header[24] == 1 && header[25] == 0){
			this.numChannels = ChannelMode.DUAL;
		} else {
			this.numChannels = ChannelMode.MONO;
		}

		//get the mode extension-only applies for joint stereo
		if(this.numChannels == ChannelMode.JOINT_STEREO){
			this.MSStereo = header[26] == 1;
			this.intensityStereo = header[27] == 1;
		}

		//don't care if the file is copyrighted (1 bit), as I'm not copying the file
		//don't care if the file is original (1 bit)
		//the emphasis doesn't really apply (2 bits)
		//the header is now done (first 4 bytes)
		int index = 4;
		
		//if there is the crc protection, skip ahead 16 bytes
		if(crcProt){ index += 16; }

		//next part, the side information 
		//(17 bytes for single channel, 32 bytes otherwise)
		if(this.numChannels == ChannelMode.MONO){
			byte[] sideBits = toBits(data, index, 17);
			int bitIndex = 0;
			
			//main data begin (9 bits)
			//this specifies the negative offset from the first byte of the 
			//synchronization word. (the start of the header for the frame).
			//this is an unsigned number
			int mainDataBegin = 0;
			for(int i = 0; i < 9; i++){
				//msb (most significant bit) is first
				mainDataBegin |= (int)sideBits[i] << (8-i); 
			}
			System.out.println("main data begin: " + mainDataBegin);
			bitIndex += 9;
			
			//the next 5 bits are for private use, and have no value to the decoder
			bitIndex += 5;
			
			//the next 4 bits specify the scale factor selection information.
			//this determines if the same scalefactors are transferred for both granules or not.
			//these four groups are transmitted if their values are 0
			//group 0 is bands 0, 1, 2, 3, 4, 5
			//group 1 is bands 6, 7, 8, 9, 10
			//group 2 is bands 11, 12, 13, 14, 15
			//group 3 is bands 16, 17, 18, 19, 20
			//if short windows are used in any granule/channel, 
			//	the scalefactors are always sent for each granule in the channel
			boolean group0, group1, group2, group3;
			group0 = sideBits[bitIndex] == 0;
			group1 = sideBits[bitIndex + 1] == 0;
			group2 = sideBits[bitIndex + 2] == 0;
			group3 = sideBits[bitIndex + 3] == 0;
			bitIndex += 4;
			System.out.printf("Groups: %b %b %b %b", group0, group1, group2, group3);
			
			//the next 12 bits are the number of bits in the main data for
			//scalefactors and huffman encoded data
			//Used to calculate the location of the next granule and the ancillary information
			int sizeSF_HE = 0;
			sizeSF_HE |= sideBits[bitIndex] << 11;
			sizeSF_HE |= sideBits[bitIndex + 1] << 10;
			sizeSF_HE |= sideBits[bitIndex + 2] << 9;
			sizeSF_HE |= sideBits[bitIndex + 3] << 8;
			sizeSF_HE |= sideBits[bitIndex + 4] << 7;
			sizeSF_HE |= sideBits[bitIndex + 5] << 6;
			sizeSF_HE |= sideBits[bitIndex + 6] << 5;
			sizeSF_HE |= sideBits[bitIndex + 7] << 4;
			sizeSF_HE |= sideBits[bitIndex + 8] << 3;
			sizeSF_HE |= sideBits[bitIndex + 9] << 2;
			sizeSF_HE |= sideBits[bitIndex + 10] << 1;
			sizeSF_HE |= sideBits[bitIndex + 11];
			bitIndex += 12;
			System.out.println("Size of scalefactors and hufman encoded: " + sizeSF_HE);
			
			//the next 9 bits are used to indicate the 
			//size of the bit values partition in the main data
			int sizeBigValues = getUnsignedInt(sideBits, bitIndex, 9);
			bitIndex += 9;
			System.out.println("Size of big values: " + sizeBigValues);
			
			//the next 8 bits specify the global_gain 
			//Specifies the quantization step size, this is needed in the 
			//requantization block of the decoder.
			int sizeGlobalGain = getUnsignedInt(sideBits, bitIndex, 8);
			bitIndex += 8;
			System.out.println("Size of global gain: " + sizeGlobalGain);
			
			//the next 4 bits specify the number of bits used for scale factor bands.
			//the 2 groups are either 0-10, 11-20 for long windows and 0-6, 7-11 for short windows.
			int temp = getUnsignedInt(sideBits, bitIndex, 4);
			bitIndex += 4;
			int scaleFactorBits1 = 0, scaleFactorBits2 = 0;
			//the table to set the values:
			switch(temp){
			case 0:scaleFactorBits1 = 0; scaleFactorBits2 = 0; break;
			case 1:scaleFactorBits1 = 0; scaleFactorBits2 = 1; break;
			case 2:scaleFactorBits1 = 0; scaleFactorBits2 = 2; break;
			case 3:scaleFactorBits1 = 0; scaleFactorBits2 = 3; break;
			case 4:scaleFactorBits1 = 3; scaleFactorBits2 = 0; break;
			case 5:scaleFactorBits1 = 1; scaleFactorBits2 = 1; break;
			case 6:scaleFactorBits1 = 1; scaleFactorBits2 = 2; break;
			case 7:scaleFactorBits1 = 1; scaleFactorBits2 = 3; break;
			case 8:scaleFactorBits1 = 2; scaleFactorBits2 = 1; break;
			case 9:scaleFactorBits1 = 2; scaleFactorBits2 = 2; break;
			case 10:scaleFactorBits1 = 2; scaleFactorBits2 = 3; break;
			case 11:scaleFactorBits1 = 3; scaleFactorBits2 = 1; break;
			case 12:scaleFactorBits1 = 3; scaleFactorBits2 = 2; break;
			case 13:scaleFactorBits1 = 3; scaleFactorBits2 = 3; break;
			case 14:scaleFactorBits1 = 4; scaleFactorBits2 = 2; break;
			case 15:scaleFactorBits1 = 4; scaleFactorBits2 = 3; break;
			}
			System.out.println("Scale factor bits: " + scaleFactorBits1 + " and " + scaleFactorBits2);
			
			//the next windows_switching_flag (1 bit, 2 bits)
			//if this is set, then block_type, mixed_block_flag and subblock_gain are used
			boolean windowSwitching = sideBits[bitIndex] == 1;
			bitIndex += 1;
			System.out.println("windows are switched? : " + windowSwitching);
			
			//TODO the rest of the side block for 1-channel
			
		} else {
			byte[] sideBits = toBits(data, index, 32);
		}
		
		//TODO - on page 20 of the pdf on side information
		
		/*
				TODO
		notes:

		All mp3 files are divided into frames, each of which stores 1152 audio samples and lasts 
		for ~26 ms. 
		(depends on sample rate)
		around 38 frames per second must be decoded to keep up with playing the audio

		each frame is divided into 2 granules each containing 576 samples

		the size of each frame (in bytes) should be: 144 * bitrate / sampleFrequency + paddingBytes
		the padding bytes are used in some frames to exactly satisfy bitrate requirements

		Structure of the first frame:

		Byte Content
		0-3 Standard audio frame header (as descripted above). Mostly it contains values 
		FF FB 30 4C, from which you can count FrameLen = 156 Bytes. 
		And thats exactly enough space for storing VBR info.
		This header contains some important information valid for the whole file:
		- MPEG (MPEG1 or MPEG2)
		- SAMPLING rate frequency index
		- CHANNEL (JointStereo etc.)

		4-x Not used till string "Xing" (58 69 6E 67). This string is used as a main VBR file identifier.
		 If it is not found, file is supposed to be CBR. 
		 This string can be placed at different locations according to values of MPEG and CHANNEL
		36-39 "Xing" for MPEG1 and CHANNEL != mono (mostly used)
		21-24 "Xing" for MPEG1 and CHANNEL == mono
		21-24 "Xing" for MPEG2 and CHANNEL != mono
		13-16 "Xing" for MPEG2 and CHANNEL == mono

		After "Xing" string there are placed flags, number of frames in file and a size of file in Bytes. 
		Each of these items has 4 Bytes and it is stored as 'int' number in memory. 
		The first is the most significant Byte and the last is the least.

		--------------------------------------------------------------------------------
		Following schema is for MPEG1 and CHANNEL != mono:
		40-43 Flags
		Value Name Description
		00 00 00 01 Frames Flag set if value for number of frames in file is stored
		00 00 00 02 Bytes Flag set if value for filesize in Bytes is stored
		00 00 00 04 TOC Flag set if values for TOC (see below) are stored
		00 00 00 08 VBR Scale Flag set if values for VBR scale are stored
		All these values can be stored simultaneously.

		44-47 Frames
		Number of frames in file (including the first info one)

		48-51 Bytes
		File length in Bytes

		52-151 TOC (Table of Contents)
		Contains of 100 indexes (one Byte length) for easier lookup in file. Approximately solves problem with moving inside file.
		Each Byte has a value according this formula:
		(TOC[i] / 256) * fileLenInBytes
		So if song lasts eg. 240 sec. and you want to jump to 60. sec. (and file is 5 000 000 Bytes length) you can use:
		TOC[(60/240)*100] = TOC[25]
		and corresponding Byte in file is then approximately at:
		(TOC[25]/256) * 5000000

		If you want to trim VBR file you should also reconstruct Frames, Bytes and TOC properly.

		152-155 VBR Scale
		I dont know exactly system of storing of this values but this item probably doesnt have deeper meaning.


		 */
	}

	/**
	 * Converts the byte array of data to an array of bits.
	 * @param data The data to convert to bits.
	 * @param start The start index to convert
	 * @param length The length of bytes to convert
	 * @return A byte array, each element is either a 1 or a 0.
	 * The first byte passed in comprises the first 8 bits.
	 */
	private byte[] toBits(byte[] data, int start, int length){
		byte[] value = new byte[length * 8];
		for(int i = 0; i < length; i++){
			for(int j = 0; j < 8; j++){
				byte temp = (byte)((data[i+start] >> (7-j)) & 0x1);
				value[i * 8 + j] = temp;
			}
		}
		return value;
	}

	/**
	 * Gets the bit rate (the number of bits per second)
	 * @param bits the 4 bits that represent this number.
	 * @return The bit rate, in number of bits per second.
	 * @throws IOException If the bits signify a 'bad' bitrate (0b1111)
	 */
	private int getBitRate(byte...bits) throws IOException {
		int code = 0;
		code |= (int)bits[0] << 3;
		code |= (int)bits[1] << 2;
		code |= (int)bits[2] << 1;
		code |= (int)bits[3];
		if(this.mpeg_1){
			switch(code){
			case 0b0000:throw new IOException("Free bitrate??");
			case 0b0001:return 32000;
			case 0b0010:return 40000;
			case 0b0011:return 48000;
			case 0b0100:return 56000;
			case 0b0101:return 64000;
			case 0b0110:return 80000;
			case 0b0111:return 96000;
			case 0b1000:return 112000;
			case 0b1001:return 128000;
			case 0b1010:return 160000;
			case 0b1011:return 192000;
			case 0b1100:return 224000;
			case 0b1101:return 256000;
			case 0b1110:return 320000;
			default:throw new IOException("'bad' bitrate??");
			}
		} else {
			switch(code){
			case 0b0000:throw new IOException("Free bitrate??");
			case 0b0001:return 8000;
			case 0b0010:return 16000;
			case 0b0011:return 24000;
			case 0b0100:return 32000;
			case 0b0101:return 64000;
			case 0b0110:return 80000;
			case 0b0111:return 56000;
			case 0b1000:return 64000;
			case 0b1001:return 128000;
			case 0b1010:return 160000;
			case 0b1011:return 192000;
			case 0b1100:return 224000;
			case 0b1101:return 256000;
			case 0b1110:return 320000;
			default:throw new IOException("'bad' bitrate??");
			}
		}
		
	}

	/**
	 * Gets the unsigned number resulting from the bits.
	 * The max length should be is 31, otherwise the int will be signed.
	 * @param bits The byte array, where each element is either 0 or 1.
	 * @param start The start index in the bits.
	 * @param length The length of the bits to use.
	 * @return the number that is equal to <code>0b bit1 bit2 bit3, ...</code>
	 */
	private int getUnsignedInt(byte[] bits, int start, int length){
		if(length > 30){
			//the integer will be signed, throw an exception
			throw new IllegalArgumentException("length field in getUnsignedInt(...) is: " + length);
		}
		int value = 0;
		for(int i = 0; i < length; i++){
			value |= ((int)bits[start + i]) << (length - i - 1);
		}
		return value;
	}
}

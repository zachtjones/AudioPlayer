package runner;

import java.io.IOException;
import java.util.Arrays;

/**
 * Represents a frame of data in an mp3 file. 
 * Each frame has 1152 audio samples
 * @author zachjones
 *
 */
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
	
	public enum WindowSwitching {
		NORMAL, START, SHORT_3, END
	}
	
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
	/** The number of bytes offset before the expected end.*/
	private int mainDataBegin;

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
			//block type
			WindowSwitching switchType = WindowSwitching.NORMAL;
			if(windowSwitching){
				if(sideBits[bitIndex] == 0){
					switchType = WindowSwitching.NORMAL;
				} else {
					if(sideBits[bitIndex + 1] == 0){
						switchType = WindowSwitching.SHORT_3;
					} else {
						switchType = WindowSwitching.END;
					}
				}
				
				bitIndex += 2;
			}
			System.out.println("switch type: " + switchType);
			//mixed block flag - 1 bit
			boolean mixedBlocks;
			if(windowSwitching){
				mixedBlocks = sideBits[bitIndex] == 1;
				bitIndex++;
			}
			/*
			The mixed_block_flag indicates that different types of windows are used in the lower
			 and higher frequencies. 
			 If mixed_block_flag is set the two lowest subbands are transformed
			  using a normal window and the remaining 30 subbands are transformed 
			  using the window specified by the block_type variable.
			*/
			
			//the huffman table select region
			int[] tableSelect;
			if(windowSwitching){
				//only need 2 tables with window switching in mono
				tableSelect = new int[2];
			} else {
				//need 3 tables
				tableSelect = new int[3];
			}
			//load in the table selections
			for(int i = 0; i < tableSelect.length; i++){
				//each is 5 bits
				tableSelect[i] |= sideBits[bitIndex] << 4;
				tableSelect[i] |= sideBits[bitIndex + 1] << 3;
				tableSelect[i] |= sideBits[bitIndex + 2] << 2;
				tableSelect[i] |= sideBits[bitIndex + 3] << 1;
				tableSelect[i] |= sideBits[bitIndex + 4];
				bitIndex += 5;
			}
			System.out.println("Table selections: " + Arrays.toString(tableSelect));
			
			int[] subblockGains = new int[tableSelect.length];
			//subblock gain - same length as the tables
			//this is the gain offset from global gain
			if(windowSwitching && switchType == WindowSwitching.SHORT_3){
				subblockGains[0] |= sideBits[bitIndex] << 2;
				subblockGains[0] |= sideBits[bitIndex + 1] << 1;
				subblockGains[0] |= sideBits[bitIndex + 2];
				subblockGains[1] |= sideBits[bitIndex + 3] << 2;
				subblockGains[1] |= sideBits[bitIndex + 4] << 1;
				subblockGains[1] |= sideBits[bitIndex + 5];
				subblockGains[2] |= sideBits[bitIndex + 6] << 2;
				subblockGains[2] |= sideBits[bitIndex + 7] << 1;
				subblockGains[2] |= sideBits[bitIndex + 8];
				bitIndex += 9;
			}
			
			//4 bits, the number of bands in the first region
			int region1Bands = 0;
			region1Bands |= sideBits[bitIndex] << 3;
			region1Bands |= sideBits[bitIndex + 1] << 2;
			region1Bands |= sideBits[bitIndex + 2] << 1;
			region1Bands |= sideBits[bitIndex + 3];
			region1Bands += 1; //always increase by one
			bitIndex += 4;
			
			//3 bits, the number of bands in the second region
			int region2Bands = 0;
			region2Bands |= sideBits[bitIndex] << 2;
			region2Bands |= sideBits[bitIndex + 1] << 1;
			region2Bands |= sideBits[bitIndex + 2];
			region2Bands += 1;
			bitIndex += 3;
			
			boolean amplifyHighFreqs = false;
			if(switchType != WindowSwitching.SHORT_3){
				amplifyHighFreqs = sideBits[bitIndex] == 1;
				bitIndex++;
			}
			
			//the logarithmic step size
			double scaleFactorScale = 2.0;
			if(sideBits[bitIndex] == 0){
				scaleFactorScale = Math.sqrt(2.0);
			}
			bitIndex++;
			
			//the next bit is if the 1st region has alternative huffman table
			boolean altTable1 = false;
			if(sideBits[bitIndex] == 1){
				altTable1 = true;
			}
			
			index += 17;
			
		} else {
			byte[] sideBits = toBits(data, index, 32);
			int bitIndex = 0;
			
			//main data begin (9 bits)
			//this specifies the negative offset from the first byte of the 
			//synchronization word. (the start of the header for the frame).
			//this is an unsigned number
			for(int i = 0; i < 9; i++){
				//msb (most significant bit) is first
				mainDataBegin |= (int)sideBits[i] << (8-i); 
			}
			System.out.println("main data begin: " + mainDataBegin);
			bitIndex += 9;
			
			//the next 3 bits are for private use, and have no value to the decoder
			bitIndex += 3;
			
			//the next 4, and then 4 bits specify the scale factor selection information.
			//for left and then right channels
			//this determines if the same scalefactors are transferred for both granules or not.
			//these four groups are transmitted if their values are 0
			//group 0 is bands 0, 1, 2, 3, 4, 5
			//group 1 is bands 6, 7, 8, 9, 10
			//group 2 is bands 11, 12, 13, 14, 15
			//group 3 is bands 16, 17, 18, 19, 20
			//if short windows are used in any granule/channel, 
			//	the scalefactors are always sent for each granule in the channel
			boolean group0L, group0R, group1L, group1R, group2L, group2R, group3L, group3R;
			group0L = sideBits[bitIndex] == 0;
			group1L = sideBits[bitIndex + 1] == 0;
			group2L = sideBits[bitIndex + 2] == 0;
			group3L = sideBits[bitIndex + 3] == 0;
			bitIndex += 4;
			group0R = sideBits[bitIndex] == 0;
			group1R = sideBits[bitIndex + 1] == 0;
			group2R = sideBits[bitIndex + 2] == 0;
			group3R = sideBits[bitIndex + 3] == 0;
			bitIndex += 4;
			System.out.printf("Groups left: %b %b %b %b", group0L, group1L, group2L, group3L);
			System.out.println();
			System.out.printf("Groups right: %b %b %b %b", group0R, group1R, group2R, group3R);
			System.out.println();
			
			//the next 12 * 2 bits are the number of bits in the main data for
			//scalefactors and huffman encoded data
			//Used to calculate the location of the next granule and the ancillary information
			int sizeSF_HE_Left = 0;
			sizeSF_HE_Left |= sideBits[bitIndex] << 11;
			sizeSF_HE_Left |= sideBits[bitIndex + 1] << 10;
			sizeSF_HE_Left |= sideBits[bitIndex + 2] << 9;
			sizeSF_HE_Left |= sideBits[bitIndex + 3] << 8;
			sizeSF_HE_Left |= sideBits[bitIndex + 4] << 7;
			sizeSF_HE_Left |= sideBits[bitIndex + 5] << 6;
			sizeSF_HE_Left |= sideBits[bitIndex + 6] << 5;
			sizeSF_HE_Left |= sideBits[bitIndex + 7] << 4;
			sizeSF_HE_Left |= sideBits[bitIndex + 8] << 3;
			sizeSF_HE_Left |= sideBits[bitIndex + 9] << 2;
			sizeSF_HE_Left |= sideBits[bitIndex + 10] << 1;
			sizeSF_HE_Left |= sideBits[bitIndex + 11];
			bitIndex += 12;
			int sizeSF_HE_Right = 0;
			sizeSF_HE_Right |= sideBits[bitIndex] << 11;
			sizeSF_HE_Right |= sideBits[bitIndex + 1] << 10;
			sizeSF_HE_Right |= sideBits[bitIndex + 2] << 9;
			sizeSF_HE_Right |= sideBits[bitIndex + 3] << 8;
			sizeSF_HE_Right |= sideBits[bitIndex + 4] << 7;
			sizeSF_HE_Right |= sideBits[bitIndex + 5] << 6;
			sizeSF_HE_Right |= sideBits[bitIndex + 6] << 5;
			sizeSF_HE_Right |= sideBits[bitIndex + 7] << 4;
			sizeSF_HE_Right |= sideBits[bitIndex + 8] << 3;
			sizeSF_HE_Right |= sideBits[bitIndex + 9] << 2;
			sizeSF_HE_Right |= sideBits[bitIndex + 10] << 1;
			sizeSF_HE_Right |= sideBits[bitIndex + 11];
			bitIndex += 12;
			System.out.println("Size of scalefactors and hufman encoded left: " + sizeSF_HE_Left);
			System.out.println("Size of scalefactors and hufman encoded right: " + sizeSF_HE_Right);
			
			//the next 9 * 2 bits are used to indicate the 
			//size of the bit values partition in the main data
			int sizeBigValuesL = getUnsignedInt(sideBits, bitIndex, 9);
			bitIndex += 9;
			int sizeBigValuesR = getUnsignedInt(sideBits, bitIndex, 9);
			bitIndex += 9;
			System.out.println("Size of big values L: " + sizeBigValuesL);
			System.out.println("Size of big values R: " + sizeBigValuesR);
			
			//the next 8 * 2 bits specify the global_gain 
			//Specifies the quantization step size, this is needed in the 
			//requantization block of the decoder.
			int sizeGlobalGainL = getUnsignedInt(sideBits, bitIndex, 8);
			bitIndex += 8;
			int sizeGlobalGainR = getUnsignedInt(sideBits, bitIndex, 8);
			bitIndex += 8;
			System.out.println("Size of global gain L: " + sizeGlobalGainL);
			System.out.println("Size of global gain R: " + sizeGlobalGainR);
			
			//the next 4 * 2 bits specify the number of bits used for scale factor bands.
			//the 2 groups are either 0-10, 11-20 for long windows and 0-6, 7-11 for short windows.
			int temp = getUnsignedInt(sideBits, bitIndex, 4);
			bitIndex += 4;
			int scaleFactorBits1L = 0, scaleFactorBits2L = 0;
			//the table to set the values:
			switch(temp){
			case 0:scaleFactorBits1L = 0; scaleFactorBits2L = 0; break;
			case 1:scaleFactorBits1L = 0; scaleFactorBits2L = 1; break;
			case 2:scaleFactorBits1L = 0; scaleFactorBits2L = 2; break;
			case 3:scaleFactorBits1L = 0; scaleFactorBits2L = 3; break;
			case 4:scaleFactorBits1L = 3; scaleFactorBits2L = 0; break;
			case 5:scaleFactorBits1L = 1; scaleFactorBits2L = 1; break;
			case 6:scaleFactorBits1L = 1; scaleFactorBits2L = 2; break;
			case 7:scaleFactorBits1L = 1; scaleFactorBits2L = 3; break;
			case 8:scaleFactorBits1L = 2; scaleFactorBits2L = 1; break;
			case 9:scaleFactorBits1L = 2; scaleFactorBits2L = 2; break;
			case 10:scaleFactorBits1L = 2; scaleFactorBits2L = 3; break;
			case 11:scaleFactorBits1L = 3; scaleFactorBits2L = 1; break;
			case 12:scaleFactorBits1L = 3; scaleFactorBits2L = 2; break;
			case 13:scaleFactorBits1L = 3; scaleFactorBits2L = 3; break;
			case 14:scaleFactorBits1L = 4; scaleFactorBits2L = 2; break;
			case 15:scaleFactorBits1L = 4; scaleFactorBits2L = 3; break;
			}
			System.out.println("Scale factor bits L: " + scaleFactorBits1L + 
					" and " + scaleFactorBits2L);
			temp = getUnsignedInt(sideBits, bitIndex, 4);
			bitIndex += 4;
			int scaleFactorBits1R = 0, scaleFactorBits2R = 0;
			//the table to set the values:
			switch(temp){
			case 0:scaleFactorBits1R = 0; scaleFactorBits2R = 0; break;
			case 1:scaleFactorBits1R = 0; scaleFactorBits2R = 1; break;
			case 2:scaleFactorBits1R = 0; scaleFactorBits2R = 2; break;
			case 3:scaleFactorBits1R = 0; scaleFactorBits2R = 3; break;
			case 4:scaleFactorBits1R = 3; scaleFactorBits2R = 0; break;
			case 5:scaleFactorBits1R = 1; scaleFactorBits2R = 1; break;
			case 6:scaleFactorBits1R = 1; scaleFactorBits2R = 2; break;
			case 7:scaleFactorBits1R = 1; scaleFactorBits2R = 3; break;
			case 8:scaleFactorBits1R = 2; scaleFactorBits2R = 1; break;
			case 9:scaleFactorBits1R = 2; scaleFactorBits2R = 2; break;
			case 10:scaleFactorBits1R = 2; scaleFactorBits2R = 3; break;
			case 11:scaleFactorBits1R = 3; scaleFactorBits2R = 1; break;
			case 12:scaleFactorBits1R = 3; scaleFactorBits2R = 2; break;
			case 13:scaleFactorBits1R = 3; scaleFactorBits2R = 3; break;
			case 14:scaleFactorBits1R = 4; scaleFactorBits2R = 2; break;
			case 15:scaleFactorBits1R = 4; scaleFactorBits2R = 3; break;
			}
			System.out.println("Scale factor bits R: " + scaleFactorBits1R + 
					" and " + scaleFactorBits2R);
			
			//the next windows_switching_flag 1 * 2 bits
			//if this is set, then block_type, mixed_block_flag and subblock_gain are used
			boolean windowSwitchingL = sideBits[bitIndex] == 1;
			bitIndex += 1;
			boolean windowSwitchingR = sideBits[bitIndex] == 1;
			bitIndex += 1;
			//block type
			WindowSwitching switchTypeL = WindowSwitching.NORMAL;
			if(windowSwitchingL){
				if(sideBits[bitIndex] == 0){
					switchTypeL = WindowSwitching.NORMAL;
				} else {
					if(sideBits[bitIndex + 1] == 0){
						switchTypeL = WindowSwitching.SHORT_3;
					} else {
						switchTypeL = WindowSwitching.END;
					}
				}
				
				bitIndex += 2;
			}
			System.out.println("switch type L: " + switchTypeL);
			WindowSwitching switchTypeR = WindowSwitching.NORMAL;
			if(windowSwitchingR){
				if(sideBits[bitIndex] == 0){
					switchTypeR = WindowSwitching.NORMAL;
				} else {
					if(sideBits[bitIndex + 1] == 0){
						switchTypeR = WindowSwitching.SHORT_3;
					} else {
						switchTypeR = WindowSwitching.END;
					}
				}
				
				bitIndex += 2;
			}
			System.out.println("switch type: " + switchTypeR);
			//mixed block flag - 1 bit * 2
			boolean mixedBlocksL = false;
			if(windowSwitchingL){
				mixedBlocksL = sideBits[bitIndex] == 1;
				bitIndex++;
			}
			System.out.println("Mixed blocks left: " + mixedBlocksL);
			boolean mixedBlocksR = false;
			if(windowSwitchingR){
				mixedBlocksR = sideBits[bitIndex] == 1;
				bitIndex++;
			}
			System.out.println("Mixed blocks right: " + mixedBlocksR);
			/*
			The mixed_block_flag indicates that different types of windows are used in the lower
			 and higher frequencies. 
			 If mixed_block_flag is set the two lowest subbands are transformed
			  using a normal window and the remaining 30 subbands are transformed 
			  using the window specified by the block_type variable.
			*/
			
			//the huffman table select region
			int[] tableSelectL;
			if(windowSwitchingL){
				//only need 2 tables with window switching
				tableSelectL = new int[2];
			} else {
				//need 3 tables
				tableSelectL = new int[3];
			}
			//load in the table selections
			for(int i = 0; i < tableSelectL.length; i++){
				//each is 5 bits
				tableSelectL[i] |= sideBits[bitIndex] << 4;
				tableSelectL[i] |= sideBits[bitIndex + 1] << 3;
				tableSelectL[i] |= sideBits[bitIndex + 2] << 2;
				tableSelectL[i] |= sideBits[bitIndex + 3] << 1;
				tableSelectL[i] |= sideBits[bitIndex + 4];
				bitIndex += 5;
			}
			System.out.println("Table selections left: " + Arrays.toString(tableSelectL));
			int[] tableSelectR;
			if(windowSwitchingR){
				//only need 2 tables with window switching
				tableSelectR = new int[2];
			} else {
				//need 3 tables
				tableSelectR = new int[3];
			}
			//load in the table selections
			for(int i = 0; i < tableSelectR.length; i++){
				//each is 5 bits
				tableSelectR[i] |= sideBits[bitIndex] << 4;
				tableSelectR[i] |= sideBits[bitIndex + 1] << 3;
				tableSelectR[i] |= sideBits[bitIndex + 2] << 2;
				tableSelectR[i] |= sideBits[bitIndex + 3] << 1;
				tableSelectR[i] |= sideBits[bitIndex + 4];
				bitIndex += 5;
			}
			System.out.println("Table selections right: " + Arrays.toString(tableSelectR));
			
			int[] subblockGainsL = new int[tableSelectL.length];
			//subblock gain - same length as the tables
			//this is the gain offset from global gain
			if(windowSwitchingL && switchTypeL == WindowSwitching.SHORT_3){
				subblockGainsL[0] |= sideBits[bitIndex] << 2;
				subblockGainsL[0] |= sideBits[bitIndex + 1] << 1;
				subblockGainsL[0] |= sideBits[bitIndex + 2];
				subblockGainsL[1] |= sideBits[bitIndex + 3] << 2;
				subblockGainsL[1] |= sideBits[bitIndex + 4] << 1;
				subblockGainsL[1] |= sideBits[bitIndex + 5];
				subblockGainsL[2] |= sideBits[bitIndex + 6] << 2;
				subblockGainsL[2] |= sideBits[bitIndex + 7] << 1;
				subblockGainsL[2] |= sideBits[bitIndex + 8];
				bitIndex += 9;
			}
			int[] subblockGainsR = new int[tableSelectR.length];
			//subblock gain for right
			if(windowSwitchingR && switchTypeR == WindowSwitching.SHORT_3){
				subblockGainsR[0] |= sideBits[bitIndex] << 2;
				subblockGainsR[0] |= sideBits[bitIndex + 1] << 1;
				subblockGainsR[0] |= sideBits[bitIndex + 2];
				subblockGainsR[1] |= sideBits[bitIndex + 3] << 2;
				subblockGainsR[1] |= sideBits[bitIndex + 4] << 1;
				subblockGainsR[1] |= sideBits[bitIndex + 5];
				subblockGainsR[2] |= sideBits[bitIndex + 6] << 2;
				subblockGainsR[2] |= sideBits[bitIndex + 7] << 1;
				subblockGainsR[2] |= sideBits[bitIndex + 8];
				bitIndex += 9;
			}
			
			//4 *2 bits, the number of bands in the first region
			int region1BandsL = 0;
			region1BandsL |= sideBits[bitIndex] << 3;
			region1BandsL |= sideBits[bitIndex + 1] << 2;
			region1BandsL |= sideBits[bitIndex + 2] << 1;
			region1BandsL |= sideBits[bitIndex + 3];
			region1BandsL += 1; //always increase by one
			bitIndex += 4;
			System.out.println("Region 1 bands L: " + region1BandsL);
			
			int region1BandsR = 0;
			region1BandsR |= sideBits[bitIndex] << 3;
			region1BandsR |= sideBits[bitIndex + 1] << 2;
			region1BandsR |= sideBits[bitIndex + 2] << 1;
			region1BandsR |= sideBits[bitIndex + 3];
			region1BandsR += 1; //always increase by one
			bitIndex += 4;
			System.out.println("Region 1 bands R: " + region1BandsR);
			
			//3 * 2 bits, the number of bands in the second region
			int region2BandsL = 0;
			region2BandsL |= sideBits[bitIndex] << 2;
			region2BandsL |= sideBits[bitIndex + 1] << 1;
			region2BandsL |= sideBits[bitIndex + 2];
			region2BandsL += 1;
			bitIndex += 3;
			System.out.println("Region 2 bands L: " + region2BandsL);
			
			int region2BandsR = 0;
			region2BandsR |= sideBits[bitIndex] << 2;
			region2BandsR |= sideBits[bitIndex + 1] << 1;
			region2BandsR |= sideBits[bitIndex + 2];
			region2BandsR += 1;
			bitIndex += 3;
			System.out.println("Region 2 bands R: " + region2BandsR);
			
			//next bit * 2 is to amplify the high frequencies
			boolean amplifyHighFreqsL = false;
			if(switchTypeL != WindowSwitching.SHORT_3){
				amplifyHighFreqsL = sideBits[bitIndex] == 1;
				bitIndex++;
			}
			System.out.println("Amplify high frequencies left: " + amplifyHighFreqsL);
			
			boolean amplifyHighFreqsR = false;
			if(switchTypeR != WindowSwitching.SHORT_3){
				amplifyHighFreqsR = sideBits[bitIndex] == 1;
				bitIndex++;
			}
			System.out.println("Amplify high frequencies right: " + amplifyHighFreqsR);
			
			//the logarithmic step size, 1 * 2 bits
			double scaleFactorScaleL = 2.0;
			if(sideBits[bitIndex] == 0){
				scaleFactorScaleL = Math.sqrt(2.0);
			}
			bitIndex++;
			System.out.println("scale factor step size left: " + scaleFactorScaleL);
			double scaleFactorScaleR = 2.0;
			if(sideBits[bitIndex] == 0){
				scaleFactorScaleR = Math.sqrt(2.0);
			}
			bitIndex++;
			System.out.println("scale factor step size right: " + scaleFactorScaleR);
			
			//the next bit is if the 1st region has alternative huffman table
			boolean altTable1L = false;
			if(sideBits[bitIndex] == 1){
				altTable1L = true;
			}
			bitIndex++;
			System.out.println("Alt table left: " + altTable1L);
			boolean altTable1R = false;
			if(sideBits[bitIndex] == 1){
				altTable1R = true;
			}
			System.out.println("Alt table right: " + altTable1R);
			index += 32;
			//onto main data
		}
		
		
		
	}

	/**
	 * Converts the byte array of data to an array of bits.
	 * @param data The data to convert to bits.
	 * @param start The start index to convert
	 * @param length The length of bytes to convert
	 * @return A byte array, each element is either a 1 or a 0.
	 * The first byte passed in comprises the first 8 bits.
	 */
	public static byte[] toBits(byte[] data, int start, int length){
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
	
	/**
	 * Gets the size of this frame, or in context, the offset until the next frame.
	 * @return The number of bytes offset to the next frame from this one
	 */
	public int getSize(){
		int value = 4; //the 4 header bytes
		//the crc - 16 bytes if used
		if(this.crc){
			value += 16;
		}
		
		//the main data
		System.out.println("Bits/sample " + this.bitsPerSample);
		System.out.println("Sample rate: " + this.sampleRate);
		value += 144 * this.bitsPerSample / this.sampleRate;
		
		//subtract the main data begin offset
		value -= this.mainDataBegin;
		value += 2;
		
		if(this.isPadded){
			value++; //an extra byte
		}
		return value;
	}
	
	/**
	 * Gets the sample rate.
	 * @return The number of samples per second in the data
	 */
	public int getSampleRate() { return this.sampleRate; }
	
	/**
	 * Gets the sound data for this frame.
	 * @return The 2-d byte array that contains the data.
	 * Each byte array contained is the data at each sample.
	 */
	public void loadData(byte[][] data, int offset){
		//TODO load in the data (1152 samples)
		//2 granules
		//http://cutebugs.net/files/mpeg-drafts/11172-3.pdf
	}

	/**
	 * Gets the channels for this frame.
	 * @return The ChannelMode enum value that is the channel type.
	 */
	public ChannelMode getNumChannels() {
		return numChannels;
	}

	/** Gets the number of bits in a sample of data */
	public int getBitsPerSample() {
		return this.bitsPerSample;
	}
}

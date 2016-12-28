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
	/** These are the scalefactor groups
	this determines if the same scalefactors are transferred for both granules or not.
	these four groups are transmitted if their values are 0
	group 0 is bands 0, 1, 2, 3, 4, 5
	group 1 is bands 6, 7, 8, 9, 10
	group 2 is bands 11, 12, 13, 14, 15
	group 3 is bands 16, 17, 18, 19, 20
	if short windows are used in any granule/channel, 
	the scalefactors are always sent for each granule in the channel */
	private boolean group0, group1, group2, group3, group0R, group1R, group2R, group3R;
	/**
	the next 12 bits are the number of bits in the main data for
	scalefactors and huffman encoded data
	Used to calculate the location of the next granule and the ancillary information*/
	private int sizeSF_HE, sizeSF_HE_R;
	/** The size of the big values partition*/
	private int sizeBigValues, sizeBigValuesR;
	/** Specifies the quantization step size, this is needed in the 
	requantization block of the decoder.*/
	private int sizeGlobalGain, sizeGlobalGainR;
	/** specify the number of bits used for scale factor bands.
	the 2 groups are either 0-10, 11-20 for long windows and 0-6, 7-11 for short windows. */
	private int scaleFactorBits1, scaleFactorBits2, scaleFactorBits1R, scaleFactorBits2R;
	/** if this is true, then block_type, mixed_block_flag and subblock_gain are used */
	private boolean windowSwitching, windowSwitchingR;
	/** The mixed_block_flag indicates that different types of windows are used in the lower
	and higher frequencies. 
	If mixedBlocks is true the two lowest subbands are transformed
	using a normal window and the remaining 30 subbands are transformed 
	using the window specified by the block_type variable.*/
	private boolean mixedBlocks, mixedBlocksR;
	/** The type of windows used */
	private WindowSwitching switchType, switchTypeR;
	/** The huffman table selection for the regions */
	private int[] tableSelect, tableSelectR;
	/** this is the gain offset from global gain */
	private int[] subblockGains, subblockGainsR;
	/** The number of bands in the first region */
	private int region1Bands, region1BandsR;
	/** The number of bands in the second region */
	private int region2Bands, region2BandsR;
	/** Whether or not to amplify high frequencies */
	private boolean amplifyHighFreqs, amplifyHighFreqsR;
	/** The logarithmic step size, either 2 or sqrt(2)*/
	private double scaleFactorScale, scaleFactorScaleR;
	/** Specifies if an alternate table is used for the 1st region */
	private boolean altTable1, altTable1R;

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
				throw new IOException("Expected header chunk not present: " + Arrays.toString(header));
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
		this.crc = header[15] == 1;

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
		if(this.crc){ index += 16; }

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
			bitIndex += 9;
			
			//the next 5 bits are for private use, and have no value to the decoder
			bitIndex += 5;
			
			//the next 4 bits specify the scale factor selection information.
			group0 = sideBits[bitIndex] == 0;
			group1 = sideBits[bitIndex + 1] == 0;
			group2 = sideBits[bitIndex + 2] == 0;
			group3 = sideBits[bitIndex + 3] == 0;
			bitIndex += 4;
			
			//the next 12 bits are the number of bits in the main data for
			//scalefactors and huffman encoded data
			//Used to calculate the location of the next granule and the ancillary information
			this.sizeSF_HE = 0;
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
			
			//the next 9 bits are used to indicate the 
			//size of the bit values partition in the main data
			this.sizeBigValues = getUnsignedInt(sideBits, bitIndex, 9);
			bitIndex += 9;
			
			//the next 8 bits specify the global_gain 
			//Specifies the quantization step size, this is needed in the 
			//requantization block of the decoder.
			this.sizeGlobalGain = getUnsignedInt(sideBits, bitIndex, 8);
			bitIndex += 8;
			
			//the next 4 bits specify the number of bits used for scale factor bands.
			//the 2 groups are either 0-10, 11-20 for long windows and 0-6, 7-11 for short windows.
			int temp = getUnsignedInt(sideBits, bitIndex, 4);
			bitIndex += 4;
			this.scaleFactorBits1 = 0;
			this.scaleFactorBits2 = 0;
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
			
			//the next windows_switching_flag (1 bit, 2 bits)
			//if this is set, then block_type, mixed_block_flag and subblock_gain are used
			this.windowSwitching = sideBits[bitIndex] == 1;
			bitIndex += 1;
			//block type
			this.switchType = WindowSwitching.NORMAL;
			if(windowSwitching){
				if(sideBits[bitIndex] == 0){
					switchType = WindowSwitching.START;
				} else {
					if(sideBits[bitIndex + 1] == 0){
						switchType = WindowSwitching.SHORT_3;
					} else {
						switchType = WindowSwitching.END;
					}
				}
				
				bitIndex += 2;
			}
			//block type
			this.switchTypeR = WindowSwitching.NORMAL;
			if(windowSwitching){
				if(sideBits[bitIndex] == 0){
					switchTypeR = WindowSwitching.START;
				} else {
					if(sideBits[bitIndex + 1] == 0){
						switchTypeR = WindowSwitching.SHORT_3;
					} else {
						switchTypeR = WindowSwitching.END;
					}
				}
				
				bitIndex += 2;
			}
			
			//mixed block flag - 1 bit
			this.mixedBlocks = false;
			if(windowSwitching){
				this.mixedBlocks = sideBits[bitIndex] == 1;
				bitIndex++;
			}
			
			//the huffman table select region
			if(windowSwitching){
				//only need 2 tables with window switching in mono
				this.tableSelect = new int[2];
			} else {
				//need 3 tables
				this.tableSelect = new int[3];
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
			
			this.subblockGains = new int[tableSelect.length];
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
			this.region1Bands = 0;
			region1Bands |= sideBits[bitIndex] << 3;
			region1Bands |= sideBits[bitIndex + 1] << 2;
			region1Bands |= sideBits[bitIndex + 2] << 1;
			region1Bands |= sideBits[bitIndex + 3];
			region1Bands += 1; //always increase by one
			bitIndex += 4;
			
			//3 bits, the number of bands in the second region
			this.region2Bands = 0;
			region2Bands |= sideBits[bitIndex] << 2;
			region2Bands |= sideBits[bitIndex + 1] << 1;
			region2Bands |= sideBits[bitIndex + 2];
			region2Bands += 1;
			bitIndex += 3;
			
			this.amplifyHighFreqs = false;
			if(switchType != WindowSwitching.SHORT_3){
				amplifyHighFreqs = sideBits[bitIndex] == 1;
				bitIndex++;
			}
			
			//the logarithmic step size
			this.scaleFactorScale = 2.0;
			if(sideBits[bitIndex] == 0){
				scaleFactorScale = Math.sqrt(2.0);
			}
			bitIndex++;
			
			//the next bit is if the 1st region has alternative huffman table
			this.altTable1 = false;
			if(sideBits[bitIndex] == 1){
				altTable1 = true;
			}
			
			index += 17;
			//done with header / side information
		} else {
			byte[] sideBits = toBits(data, index, 32);
			int bitIndex = 0;
			
			//main data begin (9 bits)
			//this specifies the negative offset from the first byte of the 
			//synchronization word. (the start of the header for the frame).
			//this is an unsigned number
			for(int i = 0; i < 9; i++){
				//msb (most significant bit) is first
				this.mainDataBegin |= (int)sideBits[i] << (8-i); 
			}
			bitIndex += 9;
			
			//the next 3 bits are for private use, and have no value to the decoder
			bitIndex += 3;
			
			//the next 4, and then 4 bits specify the scale factor selection information.
			group0 = sideBits[bitIndex] == 0;
			group1 = sideBits[bitIndex + 1] == 0;
			group2 = sideBits[bitIndex + 2] == 0;
			group3 = sideBits[bitIndex + 3] == 0;
			bitIndex += 4;
			group0R = sideBits[bitIndex] == 0;
			group1R = sideBits[bitIndex + 1] == 0;
			group2R = sideBits[bitIndex + 2] == 0;
			group3R = sideBits[bitIndex + 3] == 0;
			bitIndex += 4;
			
			//the next 12 * 2 bits are the number of bits in the main data for
			//scalefactors and huffman encoded data
			//Used to calculate the location of the next granule and the ancillary information
			this.sizeSF_HE = 0;
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
			this.sizeSF_HE_R = 0;
			sizeSF_HE_R |= sideBits[bitIndex] << 11;
			sizeSF_HE_R |= sideBits[bitIndex + 1] << 10;
			sizeSF_HE_R |= sideBits[bitIndex + 2] << 9;
			sizeSF_HE_R |= sideBits[bitIndex + 3] << 8;
			sizeSF_HE_R |= sideBits[bitIndex + 4] << 7;
			sizeSF_HE_R |= sideBits[bitIndex + 5] << 6;
			sizeSF_HE_R |= sideBits[bitIndex + 6] << 5;
			sizeSF_HE_R |= sideBits[bitIndex + 7] << 4;
			sizeSF_HE_R |= sideBits[bitIndex + 8] << 3;
			sizeSF_HE_R |= sideBits[bitIndex + 9] << 2;
			sizeSF_HE_R |= sideBits[bitIndex + 10] << 1;
			sizeSF_HE_R |= sideBits[bitIndex + 11];
			bitIndex += 12;
			
			//the next 9 * 2 bits are used to indicate the 
			//size of the bit values partition in the main data
			this.sizeBigValues = getUnsignedInt(sideBits, bitIndex, 9);
			bitIndex += 9;
			this.sizeBigValuesR = getUnsignedInt(sideBits, bitIndex, 9);
			bitIndex += 9;
			
			//the next 8 * 2 bits specify the global_gain 
			//Specifies the quantization step size, this is needed in the 
			//requantization block of the decoder.
			this.sizeGlobalGain = getUnsignedInt(sideBits, bitIndex, 8);
			bitIndex += 8;
			this.sizeGlobalGainR = getUnsignedInt(sideBits, bitIndex, 8);
			bitIndex += 8;
			
			//the next 4 * 2 bits specify the number of bits used for scale factor bands.
			//the 2 groups are either 0-10, 11-20 for long windows and 0-6, 7-11 for short windows.
			int temp = getUnsignedInt(sideBits, bitIndex, 4);
			bitIndex += 4;
			this.scaleFactorBits1 = 0;
			this.scaleFactorBits2 = 0;
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
			temp = getUnsignedInt(sideBits, bitIndex, 4);
			bitIndex += 4;
			this.scaleFactorBits1R = 0;
			this.scaleFactorBits2R = 0;
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
			
			//the next windows_switching_flag 1 * 2 bits
			//if this is set, then block_type, mixed_block_flag and subblock_gain are used
			this.windowSwitching = sideBits[bitIndex] == 1;
			bitIndex += 1;
			this.windowSwitchingR = sideBits[bitIndex] == 1;
			bitIndex += 1;
			//block type
			WindowSwitching switchTypeL = WindowSwitching.NORMAL;
			if(windowSwitching){
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
			//mixed block flag - 1 bit * 2
			this.mixedBlocks = false;
			if(windowSwitching){
				mixedBlocks = sideBits[bitIndex] == 1;
				bitIndex++;
			}
			this.mixedBlocksR = false;
			if(windowSwitchingR){
				mixedBlocksR = sideBits[bitIndex] == 1;
				bitIndex++;
			}
			
			//the huffman table select region
			if(windowSwitching){
				//only need 2 tables with window switching
				this.tableSelect = new int[2];
			} else {
				//need 3 tables
				this.tableSelect = new int[3];
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

			if(windowSwitchingR){
				//only need 2 tables with window switching
				this.tableSelectR = new int[2];
			} else {
				//need 3 tables
				this.tableSelectR = new int[3];
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
			
			this.subblockGains = new int[tableSelect.length];
			//subblock gain - same length as the tables
			//this is the gain offset from global gain
			if(windowSwitching && switchTypeL == WindowSwitching.SHORT_3){
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
			this.subblockGainsR = new int[tableSelectR.length];
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
			this.region1Bands = 0;
			region1Bands |= sideBits[bitIndex] << 3;
			region1Bands |= sideBits[bitIndex + 1] << 2;
			region1Bands |= sideBits[bitIndex + 2] << 1;
			region1Bands |= sideBits[bitIndex + 3];
			region1Bands += 1; //always increase by one
			bitIndex += 4;
			
			this.region1BandsR = 0;
			region1BandsR |= sideBits[bitIndex] << 3;
			region1BandsR |= sideBits[bitIndex + 1] << 2;
			region1BandsR |= sideBits[bitIndex + 2] << 1;
			region1BandsR |= sideBits[bitIndex + 3];
			region1BandsR += 1; //always increase by one
			bitIndex += 4;
			
			//3 * 2 bits, the number of bands in the second region
			this.region2Bands = 0;
			region2Bands |= sideBits[bitIndex] << 2;
			region2Bands |= sideBits[bitIndex + 1] << 1;
			region2Bands |= sideBits[bitIndex + 2];
			region2Bands += 1;
			bitIndex += 3;
			
			this.region2BandsR = 0;
			region2BandsR |= sideBits[bitIndex] << 2;
			region2BandsR |= sideBits[bitIndex + 1] << 1;
			region2BandsR |= sideBits[bitIndex + 2];
			region2BandsR += 1;
			bitIndex += 3;
			
			//next bit * 2 is to amplify the high frequencies
			this.amplifyHighFreqs = false;
			if(switchTypeL != WindowSwitching.SHORT_3){
				amplifyHighFreqs = sideBits[bitIndex] == 1;
				bitIndex++;
			}
			
			this.amplifyHighFreqsR = false;
			if(switchTypeR != WindowSwitching.SHORT_3){
				amplifyHighFreqsR = sideBits[bitIndex] == 1;
				bitIndex++;
			}
			
			//the logarithmic step size, 1 * 2 bits
			this.scaleFactorScale = 2.0;
			if(sideBits[bitIndex] == 0){
				scaleFactorScale = Math.sqrt(2.0);
			}
			bitIndex++;

			this.scaleFactorScaleR = 2.0;
			if(sideBits[bitIndex] == 0){
				scaleFactorScaleR = Math.sqrt(2.0);
			}
			bitIndex++;
			
			//the next bit is if the 1st region has alternative huffman table
			this.altTable1 = false;
			if(sideBits[bitIndex] == 1){
				altTable1 = true;
			}
			bitIndex++;
			this.altTable1R = false;
			if(sideBits[bitIndex] == 1){
				altTable1R = true;
			}
			index += 32;
			//done with loading header / side information
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
		//TODO - issue, the next frame is not found properly
		int value = 4; //the 4 header bytes
		
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
		//each sample in the data is 2 bytes for 1 channel, 4 bytes for 2 channel
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

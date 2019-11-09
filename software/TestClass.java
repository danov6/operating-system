package s340.software;

import s340.hardware.device.Disk;

public class TestClass {

	public static void main(String[] args) {
		System.out.println(remainingLength(455, 5, 5, 100));
	}

	public static int remainingLength(int length, int count, int total, int bufferLength) {
		int trueLength = length;
		if (count == total) {
			if (total > 1) {
				for (int i = 1; i < total; i++) {
					trueLength -= bufferLength;
				}
			} 
			else {
				trueLength = length;
			}
		} 
		else {
			for (int i = 1; i < count; i++) {
				trueLength -= bufferLength;
			}
		}

		return trueLength;
	}
}

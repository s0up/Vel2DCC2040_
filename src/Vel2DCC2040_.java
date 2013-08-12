import java.awt.datatransfer.*;

import ij.plugin.PlugIn;
import ij.plugin.AVI_Reader;
import java.awt.*;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.FileInfo;
import ij.io.FileOpener;
import ij.measure.*;
import java.io.*;

import javax.swing.JOptionPane;

import jxl.Workbook;
import jxl.write.Label;
import jxl.write.Number;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import jxl.write.biff.RowsExceededException;
import javax.swing.*;
import java.io.File;

public class Vel2DCC2040_ implements PlugIn {

	public void run(String arg) {

		// variables declaration

		File directory = new File(
				"C:\\Users\\Shawnzymlo\\Desktop\\Research\\Java\\videos");
		File[] myarray;
		myarray = directory.listFiles();
		

		String input = JOptionPane.showInputDialog("What is the frame rate");
		int Frate = Integer.parseInt(input);
		input = JOptionPane.showInputDialog("What is the magnification");
		int Mag = Integer.parseInt(input);
		
		for (int vid = 0; vid < myarray.length; vid++) {   // For loop for each video in the designated Foleder
			int width, height, xsteps, ysteps, yi, yj, dy, cont1 = 0, cont2 = 0, dir = 5;
			double max = 0, min = 0, RMSmx = 0, RMSmn = 0;
			
			File filepath = myarray[vid];
			String path = filepath.getAbsolutePath();
			System.out.println(path);
			String filename = filepath.getName();
			System.out.println(filename);
			ImagePlus imp = new ImagePlus();
			imp = AVI_Reader.open(path, false);
			imp.show();

			ImagePlus img = IJ.getImage();
			Calibration cal = img.getCalibration(); // Calibration objects
													// contain an image's
													// spatial and density
													// calibration data
			ImageProcessor ip = img.getProcessor();
			ip.setCalibrationTable(cal.getCTable());
			ip.setInterpolate(true); // setInterpolate is a method of the class
										// ImageProcessor

			ImageStack stack = img.getStack();
			int size = stack.getSize();

			String xLabel = "R (mm)";
			String yLabel = (cal != null && cal.getValueUnit() != null && !cal
					.getValueUnit().equals("Gray Value")) ? "Value ("
					+ cal.getValueUnit() + ")" : "Velocity ( mm/s)";
			double[] aux1 = new double[10];
			double[] aux2 = new double[10];
			Plot plot = new Plot("profile", xLabel, yLabel, aux1, aux2);
			PlotWindow window = plot.show();

			width = ip.getWidth();// width of the image
			height = ip.getHeight();// height of the image
			int X = 10; // width of the regions of interest
			int Y = 40; // heigth of the subtemplates
			xsteps = ((int) Math.floor(width / X));
			ysteps = ((int) Math.floor(height / Y));
			int[][] F1 = new int[height][width];
			int[][] F2 = new int[height][width];
			int[][] A = new int[height][X + 1];
			int[][] B = new int[height][X + 1];
			int[][] subA = new int[Y + 1][X + 1];
			double[] C = new double[height - Y];
			double[] C1 = new double[height - Y];
			double[][] vel = new double[ysteps][xsteps];
			int[][] velraw = new int[ysteps][xsteps];
			double[] x = new double[xsteps];
			double[] r = new double[xsteps];
			double[] r2 = new double[xsteps];
			double[] vel1 = new double[xsteps];
			double[] RMSvmax = new double[size];
			double[] RMSvmean = new double[size];
			double[][] Vprofiles = new double[size][xsteps];

			// Clipboard systemClipboard =
			// Toolkit.getDefaultToolkit().getSystemClipboard();

			for (int mySliceNumber = 1; mySliceNumber <= size - 2; mySliceNumber++) {
				// hacer esto como un metodo aparte
				for (int i = 0; i < height; i++) {
					for (int j = 0; j < width; j++) {
						F1[i][j] = ip.getPixel(j, i);// image on the frame i
					}
				}
				img.setSlice(mySliceNumber + 1);
				for (int i = 0; i < height; i++) {
					for (int j = 0; j < width; j++) {
						F2[i][j] = ip.getPixel(j, i);// image frame i+1
					}
				}

				for (int k = 0; k < xsteps; k++) {// goes through the whole
													// image in the radial
													// direction
					for (int z = 0; z < ysteps; z++) {// looks for the template
														// along the
														// longitudinal
														// direction

						for (int j = 0; j < X; j++) {
							for (int i = 0; i < height - 1; i++) {
								A[i][j] = F1[i][j + (X * k)];
								B[i][j] = F2[i][j + (X * k)];
							}
							for (int i = 0; i < Y; i++) {
								subA[i][j] = F1[i + (Y * z)][j + (X * k)];
							}
						}

						C = NormXcorr2(A, subA);// calls method NormXcorr
						C1 = NormXcorr2(B, subA);

						yi = FindMax(C);
						yj = FindMax(C1);

						dy = yi - yj;

						if (dy < 0) {
							cont1++;
						}
						if (dy > 0) {
							cont2++;
						}
						velraw[z][k] = dy;

					}// closes z

				}// closes k

				if (cont1 > cont2) {
					dir = 0;
				}
				if (cont1 < cont2) {
					dir = 1;
				}
				cont1 = cont2 = 0;

				// filter extreme values. i think this is valid but needs to be
				// modified to work for every video

				velraw = Filter1(velraw, dir);
				for (int k = 0; k < xsteps; k++) {
					for (int z = 0; z < ysteps; z++) {
						vel[z][k] = (velraw[z][k]);
					}
				}

				for (int j = 0; j < xsteps; j++) {// vertical average of
													// velocities
					x[j] = j;
					r[j] = X * x[j] * (0.125 / 1000);
					r2[j] = X * x[j] * (0.125 / 1000) + 0.3;
					for (int i = 0; i < ysteps; i++) {
						vel1[j] += vel[i][j];
					}
					vel1[j] = vel1[j] / ysteps;
					Vprofiles[mySliceNumber][j] = Math.abs(vel1[j]);
				}

				// Numeric integral of vel1 to calculate the volumetric flow
				// rate (simpsons rule)
				double Q = 0;

				for (int i = 0; i < (int) Math.floor((vel1.length) / 3); i++) {
					Q += (0.333)
							* (r[i + 1] - r[i])
							* ((vel1[i * 3] * r[i * 3])
									+ (4 * vel1[(i * 3) + 1] * r[(i * 3) + 1]) + (vel1[(i * 3) + 2] * r[(i * 3) + 2]));
				}
				Q = 2 * (3.145) * Q;// vol flow rate in mm3/s
				Q = Q * 1000;// Volumetric flow in nl/s
				Q = Math.round(Q * 100) / 100.000d;
				Q = Math.abs(Q);

				double MaxVel = 0;
				double MeanVel = 0;

				MaxVel = FindMaxV(vel1);
				MeanVel = FindMean(vel1);

				MaxVel = Math.round(MaxVel * 100) / 100.000d;
				MeanVel = Math.round(MeanVel * 100) / 100.000d;
				MeanVel = Math.abs(MeanVel);

				RMSvmax[mySliceNumber] = MaxVel * MaxVel;
				RMSvmean[mySliceNumber] = MeanVel * MeanVel;

				if (mySliceNumber == size - 2) {

					RMSmx = FindMean(RMSvmax);
					RMSmn = FindMean(RMSvmean);
					RMSmx = Math.sqrt(RMSmx);
					RMSmn = Math.sqrt(RMSmn);

				}

				Plot update = new Plot("Velocity", xLabel, yLabel, r, vel1);
				if (dir == 1) {
					max = FindMaxV(vel1) + 1;
					min = -0.1;
				}
				if (dir == 0) {
					max = 0.1;
					min = -FindMaxV(vel1) - 1;
				}
				update.setLimits(r[0], r[xsteps - 1], min, max);
				update.addErrorBars(r2);
				window.drawPlot(update);
				IJ.showStatus("Blood flow is " + Q + " nl/s"); // prints the
																// calculated
																// flow rate on
																// the imagej
																// status bar

				// IJ.error("Line selection required");
				// }
			}// closes mySliceNumber

			// StringSelection cont = new StringSelection(buffer);
			// systemClipboard.setContents(cont, cont);
			WritableWorkbook workbook = null;
			try {
				workbook = Workbook.createWorkbook(new File(path + ".xls"));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			WritableSheet sheet = workbook.createSheet("First Sheet", 0);

			for (int i = 0; i < size - 2; i++) {
				for (int j = 0; j < xsteps; j++) {

					Number number = new Number(j + 1, i + 1, Vprofiles[i][j]);
					try {
						System.out.println(Vprofiles[i][j]);
						sheet.addCell(number);
					} catch (RowsExceededException e) {
						// TODO Auto-generated catch block
						System.out.print("rowsexceed error");
						e.printStackTrace();
					} catch (WriteException e) {
						// TODO Auto-generated catch block
						System.out.print("write error");
						e.printStackTrace();
					}

					if (i == 0) {
						Label label = new Label(j + 1, 0, "section " + (j + 1));
						try {
							sheet.addCell(label);
						} catch (RowsExceededException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (WriteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}

			}

			try {
				workbook.write();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				System.out.print("12write error");
				e.printStackTrace();
			}
			try {
				workbook.close();
				System.out.print("got thia far");
			} catch (WriteException e) {
				// TODO Auto-generated catch block
				System.out.print("closee error");
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				System.out.print("io error");
				e.printStackTrace();
			}
				imp.close();
		}

	}// closes Main
		// ////////////////////////////////////////////////////////////////////////////////////////////////

	// //////////////////////////////////////////////////////////////////////////////////////////////

	public static double[] NormXcorr2(int[][] x, int[][] y) {

		int hx = x.length;// height of the larger image
		int wx = x[0].length;// width of the larger image
		int hy = y.length;// height of the smaller image (number of rows)
		int wy = y[0].length; // width of the smaller image (number of columns)
		double[] C = new double[hx - hy];
		double[] nC = new double[hx - hy];
		int[][] A = new int[hy][wy];
		int[] dotx = new int[wy];
		int[] doty = new int[wy];
		double sumdotx = 0, sumdoty = 0, sumdotx1 = 0, sumdoty1 = 0, aux1 = 0, aux2 = 0;

		for (int k = 0; k < (hx - hy - 1); k++) {
			C[k] = 0;

			for (int i = 0; i < hy - 1; i++) {
				for (int j = 0; j < wy - 1; j++) {
					aux1 = y[i][j];
					aux2 = x[i + k][j];
					C[k] += (y[i][j] * x[i + k][j]);
					A[i][j] = x[i + k][j];
					sumdotx1 += (x[i + k][j] * x[i + k][j]);
					sumdoty1 += (y[i][j] * y[i][j]);
				}
			}
			nC[k] = C[k] / (Math.sqrt(sumdotx1 * sumdoty1));

			sumdotx1 = 0;
			sumdoty1 = 0;

		}

		return nC;
	}// closes method normxcorr2

	public static int FindMax(double[] x) {
		int imax = 0;
		double max = 0;
		int l = x.length;

		for (int i = 0; i < l; i++) {
			if (x[i] > max) {
				max = x[i];
				imax = i;
			}
		}

		return imax;
	}// closes method FindMax

	public static double FindMaxV(double[] x) {

		double max = 0;
		int l = x.length;

		for (int i = 0; i < l; i++) {
			if (Math.abs(x[i]) > max) {
				max = Math.abs(x[i]);
			}
		}

		return max;
	}// closes method FindMaxV

	public static int[][] Filter1(int[][] x, int dir) {
		// if dir=1 the flow is moving up if 0 is moving down
		int a = x.length;
		int b = x[0].length, min;
		int[][] xf = new int[a][b];

		for (int j = 0; j < b; j++) {
			for (int i = 0; i < a; i++) {
				if (x[i][j] > 0 && dir == 0) {// mayoria negativa
					x[i][j] = (-1) * (x[i][j]);
				} else {
					if (x[i][j] < 0 && dir == 1) {// mayoria positiva
						x[i][j] = (-1) * (x[i][j]);
					}
				}
			}
		}

		for (int j = 0; j < b; j++) {
			min = 5000;
			for (int i = 0; i < a; i++) {
				if (Math.abs(x[i][j]) != 0 && Math.abs(x[i][j]) < min) {
					min = Math.abs(x[i][j]);
				}
			}
			for (int i = 0; i < a; i++) {
				if (x[i][j] != 0 && Math.abs(x[i][j]) > 2.1 * min && dir == 0) {// note
																				// 1
					x[i][j] = (-1) * min;
				} else {
					if (x[i][j] != 0 && Math.abs(x[i][j]) > 2.1 * min
							&& dir == 1) {// note 2
						x[i][j] = min;
					}
				}
			}
		}

		xf = x;
		return xf;
	}// Closes filter

	public static double FindMean(double[] x) {
		double mean = 0;
		int n = x.length;

		for (int i = 0; i < n; i++) {
			mean += x[i];
		}
		mean = mean / n;

		return mean;
	}

}// closes Class

/*
 * Note 1: if the value is not zero and the value is more than 2 time the
 * minimum value of the column and the flow is going down stream the value at
 * i,j is -minumim.
 */


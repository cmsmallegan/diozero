package com.diozero.devices;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.diozero.api.ThermometerInterface;
import com.diozero.util.RuntimeIOException;

/**
 * @see <a href="https://www.maximintegrated.com/en/products/analog/sensors-and-sensor-interface/DS18B20.html">DS18B20 Programmable Resolution 1-Wire Digital Thermometer</a>
 * @see <a href="https://learn.adafruit.com/adafruits-raspberry-pi-lesson-11-ds18b20-temperature-sensing?view=all">Adafruit's Raspberry Pi Lesson 11. DS18B20 Temperature Sensing</a>
 * @see <a href="https://github.com/timofurrer/w1thermsensor">W1ThermSensor Python library</a>
 */
public class W1ThermSensor implements ThermometerInterface {
	private static final String BASE_DIRECTORY = "/sys/bus/w1/devices";
	private static final String SLAVE_FILE = "w1_slave";
	
	public static List<W1ThermSensor> getAvailableSensors() {
		return getAvailableSensors(BASE_DIRECTORY);
	}
	
	public static List<W1ThermSensor> getAvailableSensors(String folder) {
		Path sensor_path = FileSystems.getDefault().getPath(folder);
		Predicate<Path> is_sensor = path -> path.toFile().isDirectory() && Type.isValid(path);
		try {
			return Files.list(sensor_path).filter(is_sensor).map(W1ThermSensor::new).collect(Collectors.toList());
		} catch (IOException e) {
			throw new RuntimeIOException(e);
		}
	}
	
	private Type type;
	private Path slaveFilePath;
	private String serialNumber;
	
	private W1ThermSensor(Path path) {
		this.type = Type.valueOf(path);
		slaveFilePath = path.resolve(SLAVE_FILE);
		serialNumber = slaveFilePath.getParent().toFile().getName().split("-")[1];
	}
	
	/**
	 * Get temperature in degrees celsius
	 * @return Temperature (deg C)
	 * @throws RuntimeIOException if an I/O error occurs
	 */
	@Override
	public float getTemperature() throws RuntimeIOException {
		/*
		 * 53 01 4b 46 7f ff 0d 10 e9 : crc=e9 YES
		 * 53 01 4b 46 7f ff 0d 10 e9 t=21187
		 * (?s).*crc=[0-9a-f]+ ([A-Z]+).*t=([0-9]+)
		 */
		try {
			List<String> lines = Files.readAllLines(slaveFilePath);
			if (! lines.get(0).trim().endsWith("YES")) {
				throw new RuntimeIOException("W1 slave not ready, serial='" + serialNumber + "'");
			}
			
			return Float.parseFloat(lines.get(1).split("=")[1]) / 1000;
		} catch (IOException e) {
			throw new RuntimeIOException("I/O error reading W1 slave, serial='" + serialNumber + "'");
		}
	}
	
	public Type getType() {
		return type;
	}
	
	public String getSerialNumber() {
		return serialNumber;
	}
	
	public static enum Type {
		DS18S20(0x10), DS1822(0x22), DS18B20(0x28), DS1825(0x3B), DS28EA00(0x42), MAX31850K(0x3B);

		private static Map<Integer, Type> TYPES;
		
		private int id;
		
		private Type(int id) {
			this.id = id;
			
			addType();
		}
		
		public static boolean isValid(Path path) {
			return isValidId(path.getFileName().toString().substring(0, 2));
		}
		
		public static boolean isValidId(String idString) {
			try {
				return TYPES.containsKey(Integer.valueOf(idString, 16));
			} catch (NumberFormatException e) {
				return false;
			}
		}

		private synchronized void addType() {
			if (TYPES == null) {
				TYPES = new HashMap<>();
			}
			TYPES.put(Integer.valueOf(id), this);
		}
		
		public int getId() {
			return id;
		}
		
		public static Type valueOf(int id) {
			return TYPES.get(Integer.valueOf(id));
		}
		
		public static Type valueOf(Path path) {
			Type type = TYPES.get(Integer.valueOf(path.getFileName().toString().substring(0, 2), 16));
			if (type == null) {
				throw new IllegalArgumentException("Invalid W1ThermSensor.Type slave='" + path.toFile().getName() + "'");
			}
			return type;
		}
	}
}

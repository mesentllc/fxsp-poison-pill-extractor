package com.fedex.smartpost.system.enums;

public enum Environment {
	PROD { public String toString() { return null; } },
	DEV { public String toString() { return "L1"; } },
	L2 { public String toString() { return "L2"; } },
	L3 { public String toString() { return "L3"; } },
	L4 { public String toString() { return "L4"; } }
}

package union.utils;

public class SteamUtil {

	public static String convertSteam64toSteamID(long steam64) throws NumberFormatException {
		Long universe = (steam64 >> 56) & 0xFF;
		if (universe == 1) universe = 0L;

		Long accountIdLowBit = (steam64 - 76561197960265728L) & 1;

		Long accountIdHighBits = (steam64 - 76561197960265728L - accountIdLowBit) / 2;

		return "STEAM_" + universe + ":" + accountIdLowBit + ":" + accountIdHighBits;
	}

	public static long convertSteamIDtoSteam64(String steamId) {
		long steam64 = 76561197960265728L;
		String[] id_split = steamId.split(":");

		steam64 += Long.parseLong(id_split[2]) * 2;
		if (id_split[1].equals("1")) steam64 += 1;
		
		return steam64;
	}
	
}

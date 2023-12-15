package union.utils.message;

public class SteamUtil {

	public SteamUtil() {}

	public String convertSteam64toSteamID(String steam64) throws NumberFormatException {
		Long steamId64 = Long.valueOf(steam64);

		Long universe = (steamId64 >> 56) & 0xFF;
		if (universe == 1) universe = 0L;

		Long accountIdLowBit = (steamId64 - 76561197960265728L) & 1;

		Long accountIdHighBits = (steamId64 - 76561197960265728L - accountIdLowBit) / 2;

		return "STEAM_" + universe + ":" + accountIdLowBit + ":" + accountIdHighBits;
	}

	public String convertSteamIDtoSteam64(String steamId) {
		Long steam64 = 76561197960265728L;
		String[] id_split = steamId.split(":");

		steam64 += Long.parseLong(id_split[2]) * 2;
		if (id_split[1].equals("1")) steam64 += 1;
		
		return String.valueOf(steam64);
	}
	
}

//package gg.modl.minecraft.core.procedure;
//
//import gg.modl.minecraft.api.Account;
//import gg.modl.minecraft.api.IPAddress;
//
//import java.util.Date;
//
//public class AccountHelper {
//    public static boolean hasRealIP(Account account) {
//        for (IPAddress ip : account.ipList()) if (!ip.proxy()) return true;
//        return false;
//    }
//
//    public static Date firstLogin(Account account) {
//        Date earliestLogin = new Date();
//        for (IPAddress ip : account.ipList()) if (ip.firstLogin().getTime() < earliestLogin.getTime()) earliestLogin = ip.firstLogin();
//        return earliestLogin;
//    }
//
//    public static Date lastLogin(Account account) {
//        Date latestLogin = new Date(1);
//        for (IPAddress ip : account.ipList()) if (ip.logins().get(ip.logins().size() - 1).getTime() > latestLogin.getTime())
//            latestLogin = ip.logins().get(ip.logins().size() - 1);
//        return latestLogin;
//    }
//}

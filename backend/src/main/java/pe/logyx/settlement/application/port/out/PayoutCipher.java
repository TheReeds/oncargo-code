package pe.logyx.settlement.application.port.out;

/** Cifra el número de una cuenta de cobro en reposo (AES-GCM con clave de la configuración). */
public interface PayoutCipher {
    byte[] encrypt(String plain);

    String decrypt(byte[] sealed);
}

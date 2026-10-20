package com.bitpoint.homeservercontrol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

/** Android-compatible public-key parser; no server library is initialized during UI validation. */
final class SshPublicKeyParser {
    static final class Parsed {
        final PublicKey key; final String normalized;
        Parsed(PublicKey key,String normalized) {this.key=key;this.normalized=normalized;}
    }
    static Parsed parse(String text) throws Exception {
        if(text==null || text.length()>16384)throw new IllegalArgumentException("Нужен публичный ключ .pub");
        String[] words=text.trim().split("\\s+");
        if(words.length<2 || !(words[0].equals("ssh-rsa") || words[0].equals("ecdsa-sha2-nistp256")))throw new IllegalArgumentException("Неподдерживаемый публичный ключ");
        StringBuilder encoded=new StringBuilder();
        for(int i=1;i<words.length;i++) {
            if(!words[i].matches("[A-Za-z0-9+/]+={0,2}"))break;
            encoded.append(words[i]);byte[] raw;
            try {raw=Base64.getDecoder().decode(encoded.toString());}catch(IllegalArgumentException partial){continue;}
            try {
                ByteBuffer input=ByteBuffer.wrap(raw);String type=new String(part(input),StandardCharsets.US_ASCII);
                if(!words[0].equals(type))throw new IllegalArgumentException("Тип ключа не совпадает");
                PublicKey key;
                if(type.equals("ssh-rsa")) {
                    BigInteger exponent=new BigInteger(1,part(input)),modulus=new BigInteger(1,part(input));
                    if(modulus.bitLength()<3072 || modulus.bitLength()>8192 || exponent.compareTo(BigInteger.valueOf(3))<0 || !exponent.testBit(0))throw new IllegalArgumentException("Нужен RSA 3072–8192");
                    key=KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus,exponent));
                } else {
                    if(!new String(part(input),StandardCharsets.US_ASCII).equals("nistp256"))throw new IllegalArgumentException("Неверная EC-кривая");
                    byte[] point=part(input);if(point.length!=65 || point[0]!=4)throw new IllegalArgumentException("Неверная EC-точка");
                    AlgorithmParameters parameters=AlgorithmParameters.getInstance("EC");parameters.init(new ECGenParameterSpec("secp256r1"));
                    ECParameterSpec spec=parameters.getParameterSpec(ECParameterSpec.class);
                    BigInteger x=new BigInteger(1,java.util.Arrays.copyOfRange(point,1,33)),y=new BigInteger(1,java.util.Arrays.copyOfRange(point,33,65));
                    BigInteger prime=((ECFieldFp)spec.getCurve().getField()).getP();
                    if(x.compareTo(prime)>=0 || y.compareTo(prime)>=0 || !y.multiply(y).mod(prime).equals(x.multiply(x).multiply(x).add(spec.getCurve().getA().multiply(x)).add(spec.getCurve().getB()).mod(prime)))throw new IllegalArgumentException("Неверная EC-точка");
                    key=KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x,y),spec));
                }
                if(input.hasRemaining())throw new IllegalArgumentException("Лишние данные ключа");
                for(int j=i+1;j<words.length;j++)if(words[j].startsWith("ssh-") || words[j].startsWith("ecdsa-") || words[j].contains("PRIVATE"))throw new IllegalArgumentException("Нужен ровно один публичный ключ");
                return new Parsed(key,type+" "+Base64.getEncoder().encodeToString(raw)+" LocalLS");
            } catch(IncompleteKey partial) { }
        }
        throw new IllegalArgumentException("Повреждённый публичный ключ");
    }
    private static byte[] part(ByteBuffer b) throws IncompleteKey {
        if(b.remaining()<4)throw new IncompleteKey();int length=b.getInt();
        if(length<1 || length>8192)throw new IllegalArgumentException("Неверная длина ключа");
        if(length>b.remaining())throw new IncompleteKey();byte[] bytes=new byte[length];b.get(bytes);return bytes;
    }
    private static final class IncompleteKey extends Exception { }
}

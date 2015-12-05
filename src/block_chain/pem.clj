(ns block-chain.pem
  (:require [clojure.java.io :as io]
            [clojure.string :refer [join split]]
            [block-chain.encoding :refer :all]))

(java.security.Security/addProvider (org.bouncycastle.jce.provider.BouncyCastleProvider.))

(defn keydata [reader]
 (->> reader
      (org.bouncycastle.openssl.PEMParser.)
      (.readObject)))

(defn pem-string->key-pair [string]
  "Convert a PEM-formatted private key string to a public/private keypair.
   Returns java.security.KeyPair."
  (let [kd (keydata (io/reader (.getBytes string)))]
    (.getKeyPair (org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter.) kd)))

(defn pem-string->pub-key [string]
  "Convert a PEM-formatted public key string to an RSA public key.
   Returns sun.security.rsa.RSAPublicKeyImpl"
  (let [kd (keydata (io/reader (.getBytes string)))
        kf (java.security.KeyFactory/getInstance "RSA")
        spec (java.security.spec.X509EncodedKeySpec. (.getEncoded kd))]
    (.generatePublic kf spec)))

(defn pem-file->public-key [filepath]
  "Read a file containing a PEM-formatted public key
   and return a matching key"
  (pem-string->pub-key (slurp filepath)))

(defn pem-file->key-pair [filepath]
  "Read a file containing a PEM-formatted private key and
   return a matching keypair"
  (pem-string->key-pair (slurp filepath)))

(defn format-pem-string [encoded key-type]
  "Takes a Base64-encoded string of key data and formats it
   for file-output following openssl's convention of wrapping lines
   at 64 characters and appending the appropriate header and footer for
   the specified key type"
  (let [chunked (->> encoded
                     (partition 64 64 [])
                     (map #(apply str %)))
        formatted (join "\n" chunked)]
    (str "-----BEGIN " key-type "-----\n"
         formatted
         "\n-----END " key-type "-----\n")))

(defn private-key->pem-string [key]
  "Convert RSA private keypair to a formatted PEM string for saving in
   a .pem file. By default these private keys will encode themselves as PKCS#8
   data (e.g. when calling (.getEncoded private-key)), so we have to convert it
   to ASN1, which PEM uses (this seems to also be referred to as PKCS#1).
   More info here http://stackoverflow.com/questions/7611383/generating-rsa-keys-in-pkcs1-format-in-java"
  (-> (.getEncoded key)
      (org.bouncycastle.asn1.pkcs.PrivateKeyInfo/getInstance)
      (.parsePrivateKey)
      (.toASN1Primitive)
      (.getEncoded)
      (encode64)
      (format-pem-string "RSA PRIVATE KEY")))

(defn public-key->pem-string [key]
  "Generate PEM-formatted string for a public key. This is simply a base64
   encoding of the key wrapped with the appropriate header and footer."
  (format-pem-string (encode64 (.getEncoded key))
                     "PUBLIC KEY"))


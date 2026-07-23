using System;
using System.IO;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

namespace HomeControl.Companion.Certificates;

/// <summary>
/// Generates (once) and persists a self-signed TLS certificate for the
/// companion's WebSocket endpoint. There's no public CA involved — this is
/// a home-network, trust-on-first-use model: the Android app pins this
/// certificate's fingerprint the first time it pairs.
/// </summary>
internal static class SelfSignedCertificateProvider
{
    private const string CertificateFileName = "companion.pfx";
    private const string Subject = "CN=HomeControl Companion";

    public static X509Certificate2 GetOrCreate()
    {
        var path = Path.Combine(AppDataDirectory(), CertificateFileName);
        if (File.Exists(path))
        {
            return X509CertificateLoader.LoadPkcs12FromFile(path, password: null, X509KeyStorageFlags.PersistKeySet);
        }

        using var rsa = RSA.Create(2048);
        var request = new CertificateRequest(Subject, rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        request.CertificateExtensions.Add(
            new X509BasicConstraintsExtension(certificateAuthority: false, hasPathLengthConstraint: false, pathLengthConstraint: 0, critical: true));
        request.CertificateExtensions.Add(
            new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, critical: true));

        var certificate = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddYears(10));

        Directory.CreateDirectory(AppDataDirectory());
        File.WriteAllBytes(path, certificate.Export(X509ContentType.Pfx));

        return certificate;
    }

    public static string GetFingerprint(X509Certificate2 certificate) =>
        Convert.ToHexString(certificate.GetCertHash(HashAlgorithmName.SHA256));

    private static string AppDataDirectory() =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "HomeControl", "Companion");
}

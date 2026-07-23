using System;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Threading.Tasks;

namespace HomeControl.Companion.Pairing;

/// <summary>
/// Pairing is our own protocol, so it's fully in our control: an ECDH
/// (P-256) key exchange between phone and PC, verified by a short numeric
/// code derived from the resulting shared secret and shown on both screens.
/// Neither side ever transmits the code itself — the security comes from
/// the human visually comparing the two independently-computed values,
/// the same principle as Bluetooth/ZRTP numeric-comparison pairing. A
/// man-in-the-middle running separate exchanges with each side would end
/// up with two different shared secrets, so the codes wouldn't match.
/// </summary>
internal sealed class PairingManager
{
    private readonly ECDiffieHellman _serverKey = ECDiffieHellman.Create(ECCurve.NamedCurves.nistP256);
    private readonly TrustedClientStore _store = new();

    public event EventHandler<PairingCodeEventArgs>? PairingCodeReady;

    public string ServerPublicKeyBase64 => Convert.ToBase64String(_serverKey.PublicKey.ExportSubjectPublicKeyInfo());

    public PairingOutcome BeginPairing(string clientPublicKeyBase64, string deviceName)
    {
        var code = DeriveSasCode(clientPublicKeyBase64);
        var approvalSource = new TaskCompletionSource<bool>();

        var trustedClient = new TrustedClient
        {
            PublicKeyBase64 = clientPublicKeyBase64,
            DeviceName = deviceName,
            PairedAt = DateTimeOffset.UtcNow,
        };

        PairingCodeReady?.Invoke(this, new PairingCodeEventArgs(code, deviceName, approved =>
        {
            if (approved)
            {
                _store.Add(trustedClient);
            }
            approvalSource.TrySetResult(approved);
        }));

        return new PairingOutcome(approvalSource.Task, trustedClient);
    }

    public TrustedClient? FindTrustedClient(string clientPublicKeyBase64) => _store.Find(clientPublicKeyBase64);

    public IReadOnlyList<TrustedClient> GetTrustedClients() => _store.GetAll();

    public bool VerifyProof(string clientPublicKeyBase64, byte[] nonce, byte[] proof)
    {
        var sharedSecret = ComputeSharedSecret(clientPublicKeyBase64);
        var expected = HMACSHA256.HashData(sharedSecret, nonce);
        return CryptographicOperations.FixedTimeEquals(expected, proof);
    }

    public static byte[] GenerateNonce()
    {
        var nonce = new byte[16];
        RandomNumberGenerator.Fill(nonce);
        return nonce;
    }

    private string DeriveSasCode(string clientPublicKeyBase64)
    {
        var sharedSecret = ComputeSharedSecret(clientPublicKeyBase64);
        var hash = SHA256.HashData(sharedSecret);
        var numericValue = BitConverter.ToUInt32(hash, 0) % 1_000_000u;
        return numericValue.ToString("D6");
    }

    private byte[] ComputeSharedSecret(string clientPublicKeyBase64)
    {
        using var clientKey = ECDiffieHellman.Create();
        clientKey.ImportSubjectPublicKeyInfo(Convert.FromBase64String(clientPublicKeyBase64), out _);
        return _serverKey.DeriveKeyMaterial(clientKey.PublicKey);
    }
}

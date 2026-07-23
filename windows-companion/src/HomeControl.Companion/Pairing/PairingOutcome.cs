using System.Threading.Tasks;

namespace HomeControl.Companion.Pairing;

internal sealed record PairingOutcome(Task<bool> ApprovalTask, TrustedClient TrustedClient);

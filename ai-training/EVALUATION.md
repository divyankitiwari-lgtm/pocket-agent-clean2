# JARVIS model acceptance gates

A training run is not accepted merely because the loss decreases.

Minimum checks:
- At least 100,000 unique training examples and 10,000 held-out validation examples.
- Every one of the eight skills is represented in both splits.
- No exact user-prompt overlap between train and validation.
- JSON output parses and conforms to the planner schema.
- Tool selection is correct for held-out paraphrases and unseen targets.
- The model distinguishes app launch/UI automation from optional OAuth account connections.
- The model reports unavailable tools and missing permissions honestly.
- The model asks for confirmation before sending/publishing, deleting/overwriting, purchases, and account/security changes.
- The model never asks the user for passwords, OTPs, or payment credentials.
- Compare success rates and invalid JSON rates against the untrained base model.
- Test latency and memory on the intended Android device before choosing a deployment format.

A candidate that fails any safety or honesty gate must not be shipped, even if its training loss is lower.

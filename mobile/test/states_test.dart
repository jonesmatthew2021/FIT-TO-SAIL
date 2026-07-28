import 'package:crewcomp_crew/src/domain/states.dart';
import 'package:crewcomp_crew/src/ui/nocturne.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('cell states', () {
    test('order the worklist worst-first', () {
      expect(cellStateRank('gap'), lessThan(cellStateRank('expiring')));
      expect(cellStateRank('expiring'), lessThan(cellStateRank('ok')));
      expect(cellStateRank('ok'), lessThan(cellStateRank('na')));
    });

    test('sort an unrecognised state first rather than hiding it', () {
      // A state a future server introduces must surface, not settle quietly at the bottom of
      // a list where nobody looks.
      expect(cellStateRank('something_new'), lessThan(cellStateRank('gap')));
    });

    test('never render an unknown state as reassuring', () {
      expect(cellStateLabel('something_new'), 'something_new');
      expect(cellStateLabel('ok'), 'OK');
    });

    test('flag exactly the states a crew member should act on', () {
      expect(needsAttention('gap'), isTrue);
      expect(needsAttention('expiring'), isTrue);
      expect(needsAttention('unknown'), isTrue);
      expect(needsAttention('review'), isTrue);
      expect(needsAttention('ok'), isFalse);
      expect(needsAttention('na'), isFalse);
      // Quota-only is not the individual's problem to solve (§5.1 step 2).
      expect(needsAttention('quota_only'), isFalse);
    });

    test('carry the same six tones the console does', () {
      // The mapping is ported from `admin-web/src/domain/enums.ts`. Two clients that disagree
      // about what colour a gap is are two clients a coordinator and a crew member cannot talk
      // to each other over.
      expect(cellStateTone('gap'), Tone.critical);
      expect(cellStateTone('expiring'), Tone.warning);
      expect(cellStateTone('ok'), Tone.good);
      expect(cellStateTone('quota_only'), Tone.muted);
      // An absence of information, not a verdict — and quieter than a warning for that reason.
      expect(cellStateTone('unknown'), Tone.caution);
      expect(cellStateTone('review'), Tone.caution);
      expect(cellStateTone('pending'), Tone.caution);
      expect(cellStateTone('exempt'), Tone.neutral);
    });

    test('give gap and ok visibly different fills', () {
      expect(toneColours(cellStateTone('gap')).fill, isNot(toneColours(cellStateTone('ok')).fill));
    });

    test('label the two states the shipped build had never heard of', () {
      // `pending` and `exempt` are §5.1 states the engine emits and this app used to render
      // verbatim — a crew member reading "exempt" as a raw wire value beside "Expiring".
      expect(cellStateLabel('pending'), 'Pending');
      expect(cellStateLabel('exempt'), 'Exempt');
    });
  });

  group('crew-facing alerts', () {
    test("tones the office's reply by whether it left the crew member something to do", () {
      // Amber and not red for a dismissal: the office looked and could not act, which is work
      // returning rather than a fault — and it is the same tone the card on Home gives it.
      expect(notificationKindColour('crew_request_actioned'), Nocturne.goodText);
      expect(notificationKindColour('crew_request_dismissed'), Nocturne.warningText);
    });

    test('offers an action on the reply that hands the job back, and not on the other', () {
      // MOB-4's rule: every alert that implies an action offers it inline.
      expect(notificationActionLabel('crew_request_dismissed'), isNotNull);
      expect(notificationActionLabel('crew_request_actioned'), isNull);
    });
  });

  group('Appendix A labels', () {
    test('map holding statuses', () {
      expect(holdingStatusLabel('held_expiry'), 'Held, expires');
      expect(holdingStatusLabel('not_held'), 'Not held');
    });

    test('map the submission queue states MOB-4 shows', () {
      expect(submissionStatusLabel('pending_extraction'), 'Processing');
      expect(submissionStatusLabel('rejected'), 'Rejected');
    });

    test('leave requirement category codes alone', () {
      // Nothing in the spec says what QL/VS/PS/MS expand to, and a confident wrong expansion in
      // front of someone who knows the right one is worse than a bare code.
      expect(categoryLabel('QL'), 'QL');
      expect(categoryLabel('MS'), 'MS');
    });
  });
}

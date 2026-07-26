import 'package:crewcomp_crew/src/domain/states.dart';
import 'package:flutter/material.dart';
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

    test('give gap and ok visibly different colours in both themes', () {
      for (final brightness in Brightness.values) {
        final gap = cellStateColours('gap', brightness);
        final ok = cellStateColours('ok', brightness);
        expect(gap.background, isNot(ok.background));
      }
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

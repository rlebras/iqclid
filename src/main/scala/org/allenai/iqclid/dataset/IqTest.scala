package org.allenai.iqclid.dataset

import org.allenai.iqclid.{ DatasetSequence, _ }
import org.allenai.iqclid.api._

object IqTest extends Dataset {

  val sequences = easy

  val easy = Seq(
    DatasetSequence(
      NumberSequence(Seq(2, 4, 9, 11, 16), 1),
      18,
      Apply(Plus(), Seq(
        T(1),
        Apply(Plus(), Seq(
          Apply(Times(), Seq(
            Apply(Mod(), Seq(
              Apply(Plus(), Seq(I(), Number(1))), Number(2))),
            Number(3))),
          Number(2)))))),

    DatasetSequence(
      NumberSequence(Seq(30, 28, 25, 21, 16), 1),
      10,
      Apply(Minus(), Seq(
        T(1), Apply(Plus(), Seq(I(), Number(1)))))),

    DatasetSequence(
      NumberSequence(Seq(-972, 324, -108, 36, -12), 1),
      4,
      Apply(Div(), Seq(
        T(1), Apply(Minus(), Seq(Number(0), Number(3)))))),

    //    Commented out because we cannot handle decimals
    //    DatasetSequence(
    //      NumberSequence(Seq(0.3, 0.5, 0.8, 1.2, 1.7),
    //        2.3,
    //      Apply(Plus(), Seq(
    //        T(1), Apply(Div(), Seq(Apply(Plus(), Seq(I(), Number(1))), Number(10)))))),

    DatasetSequence(
      NumberSequence(Seq(16, 22, 34, 52, 76), 1),
      106,
      Apply(Plus(), Seq(
        T(1), Apply(Times(), Seq(Number(6), I()))))),

    DatasetSequence(
      NumberSequence(Seq(123, 135, 148, 160, 173), 1),
      185,
      Apply(Plus(), Seq(
        T(1), Apply(Plus(), Seq(Number(12), Apply(Mod(), Seq(Apply(Plus(), Seq(I(), Number(1))),
          Number(2)))))))),

    DatasetSequence(
      NumberSequence(Seq(4, 5, 7, 11, 19), 1),
      35,
      Apply(Plus(), Seq(
        T(1), Apply(Pow(), Seq(
          Number(2), Apply(Minus(), Seq(
            I(), Number(1)))))))))

  val medium = Seq(
    DatasetSequence(
      NumberSequence(Seq(-2, 5, -4, 3, 6), 2),
      1,
      Apply(Minus(), Seq(
        T(2), Number(2)))),

    DatasetSequence(
      NumberSequence(Seq(1, 4, 9, 16, 25), 1),
      36,
      Apply(Plus(), Seq(
        T(1), Apply(Plus(), Seq(Apply(Times(), Seq(
          Number(2), I())), Number(1)))))),

    DatasetSequence(
      NumberSequence(Seq(75, 15, 25, 5, 15), 1),
      3,
      Apply(Plus(),
        Seq(
      Apply(Div(), Seq(
        Apply(Times(), Seq(
          Apply(Mod(), Seq(
            I(), Number(2))),
          T(1))),
        Number(5))),
          Apply(Times(), Seq(
            Apply(Mod(), Seq(
              Apply(Plus(), Seq(
                I(), Number(1))),
              Number(2))),
            T(1))),
          Number(10)))),

    DatasetSequence(
      NumberSequence(Seq(1, 2, 6, 24, 120), 1),
      720,
      Apply(Times(), Seq(
        T(1),
        Apply(Plus(), Seq(I(), Number(1)))
      ))),

//    Too annoying to encode
//    DatasetSequence(
//      NumberSequence(Seq(183, 305, 527, 749, 961), 1),
//      720,
//      ???)

    DatasetSequence(
      NumberSequence(Seq(16, 22, 34, 58, 106), 2),
      202,
      Apply(Minus(), Seq(
        Apply(Times(), Seq(T(1), Number(3))),
        Apply(Times(), Seq(T(2), Number(2)))
      ))),

    DatasetSequence(
      NumberSequence(Seq(17, 40, 61, 80, 97), 2),
      112,
      Apply(Minus(), Seq(
        Apply(Minus(), Seq(
          Apply(Times(), Seq(T(1), Number(2))),
          T(2)
      )),
        Number(2)))),

    DatasetSequence(
      NumberSequence(Seq(55, 34, 21, 13, 8), 2),
      5,
      Apply(Minus(), Seq(
        T(2),
        T(1)
      ))),

    DatasetSequence(
      NumberSequence(Seq(259, 131, 67, 35, 19), 1),
      11,
      Apply(Div(), Seq(
        Apply(Plus(), Seq(T(1), Number(3))),
        Number(2)
      ))),


    DatasetSequence(
      NumberSequence(Seq(93, 74, 57, 42, 29), 1),
      18,
      Apply(Plus(),
        Seq(
          Apply(Minus(), Seq(T(1), Number(19))),
          Apply(Times(), Seq(I(), Number(2)))
        ))),

    DatasetSequence(
      NumberSequence(Seq(7, 21, 14, 42, 28), 2),
      84,
      Apply(Times(), Seq(
        T(2),
        Number(2)
      ))),

    DatasetSequence(
      NumberSequence(Seq(2, -12, -32, -58, -90), 2),
      -128,
      Apply(Minus(),
        Seq(
          Apply(Minus(), Seq(T(1), Number(14))),
          Apply(Times(), Seq(I(), Number(6)))
        ))),

    DatasetSequence(
      NumberSequence(Seq(0, 9, 36, 81, 144), 2),
      225,
      Apply(Pow(), Seq(
       Apply(Times(), Seq(Number(3), I())),
        Number(2)
      )))
  )
}

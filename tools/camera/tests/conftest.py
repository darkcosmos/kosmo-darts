"""Shared fixtures: a synthetic board and a synthetic session (generated data only, see kosmo_camera.synth)."""
import pytest


def _synth():
    """The synthetic generator needs av and Pillow: only the fixtures below skip without them, so the stdlib-only
    test files still run on a bare Python."""
    pytest.importorskip("av")
    pytest.importorskip("PIL")
    from kosmo_camera import synth
    return synth


def default_darts(synth):
    return (
        synth.DartSpec(20, "TRIPLE"),
        synth.DartSpec(5, "SINGLE", voided=True),  # CV5: voided, not judged
        synth.DartSpec(19, "DOUBLE"),
        synth.DartSpec(25),
        synth.DartSpec(0, photo=False),  # CV5: no photo, not judged
        synth.DartSpec(1, "SINGLE"),
    )


@pytest.fixture(scope="session")
def synthetic_session(tmp_path_factory):
    """Two volleys of three darts, seed 7: a voided dart, a dart without photo, a calibration segment from dart 4."""
    synth = _synth()
    spec = synth.SessionSpec(default_darts(synth), segments=(1, 4), drifts=(synth.Drift(4, dx=6.0, dy=-3.0, roll=0.5),))
    return synth.make_session(tmp_path_factory.mktemp("synthetic"), spec, seed=7)


@pytest.fixture
def synthetic_board():
    """Callable: ``synthetic_board(darts_mm, view=synth.View(), size=(640, 480), seed=0)`` -> (image, truth)."""
    synth = _synth()

    def render(darts_mm=(), view=None, size=(640, 480), seed=0):
        view = synth.View() if view is None else view
        return synth.render_board(view, darts_mm, size, synth.Lighting(), seed)
    return render

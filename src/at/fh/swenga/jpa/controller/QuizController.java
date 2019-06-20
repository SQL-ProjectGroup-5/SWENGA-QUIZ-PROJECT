package at.fh.swenga.jpa.controller;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.fluttercode.datafactory.impl.DataFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;

import at.fh.swenga.jpa.dao.DocumentRepository;
import at.fh.swenga.jpa.dao.QuizRepository;
import at.fh.swenga.jpa.dao.ResultRepository;
import at.fh.swenga.jpa.dao.SongRepository;
import at.fh.swenga.jpa.helper.ZXingHelper;
import at.fh.swenga.jpa.model.QuizModel;
import at.fh.swenga.jpa.model.ResultModel;
import at.fh.swenga.jpa.model.SongModel;

@Controller
public class QuizController {
	@Autowired
	QuizRepository quizRepository;
	@Autowired
	SongRepository songRepository;
	@Autowired
	DocumentRepository documentRepository;
	@Autowired
	ResultRepository resultRepository;

	@RequestMapping(value = { "/quizzes", "listquizzes" })
	public String index(Model model) {
		List<QuizModel> quizzes = quizRepository.findAll();
		model.addAttribute("quizzes", quizzes);
		return "indexQuiz";
	}

	@RequestMapping("/statistics")
	public String handleStatistics(@RequestParam(value = "gid") int gid,
			@RequestParam(value = "nickname") String nickname, @RequestParam(value = "qid", required = false) int qid,
			Model model) {
		model.addAttribute("gameIndex", gid);
		List<ResultModel> results = resultRepository
				.findBySessionIDAndQuizId(RequestContextHolder.currentRequestAttributes().getSessionId(), gid);
		model.addAttribute("results", results);
		return "gameStatistics";
	}

	@RequestMapping("/")
	public String showIndex(@RequestParam(value = "qid", required = false) Integer qid, Model model) {
		model.addAttribute("preDef", qid);
		return "login";
	}

	/*
	 * @RequestMapping("/fillquizzes")
	 * 
	 * @Transactional public String fillData(Model model) { DataFactory df = new
	 * DataFactory(); Calendar publishDate = Calendar.getInstance();
	 * publishDate.setTime(df.getDateBetween(df.getDate(2000, 1, 1),
	 * df.getDate(2019, 1, 1))); QuizModel quizModel = new QuizModel("Test", 1,
	 * publishDate); quizModel.setSongs(songRepository.findAll());
	 * quizRepository.save(quizModel); return "forward:listquizzes"; }
	 */
	@RequestMapping("/deletequiz")
	@Transactional
	public String deleteQuiz(Model model, @RequestParam int quizId) {
		quizRepository.deleteById(quizId);
		model.addAttribute("message", String.format("Deleted Quiz with ID: %d", quizId));
		return "forward:quizManagement";
	}

	@RequestMapping("/addsongtoquiz")
	@Transactional
	public String addsong(Model model, @RequestParam int quizId,
			@RequestParam(name = "songId", required = false) List<Integer> songIds) {

		if (CollectionUtils.isEmpty(songIds)) {
			model.addAttribute("errorMessage", "No songs selected!");
			return "forward:editQuiz";
		}
		QuizModel quiz = quizRepository.findById(quizId).get();

		Collection<SongModel> ss = quiz.getSongs();

		// check if a added song is already in the Collection
		for (SongModel s : ss) {
			for (int h : songIds) {
				if (s.getId() == h) {
					model.addAttribute("errorMessage", "Song is already in the quiz");
					return "forward:editQuiz";
				}
			}

		}

		quiz.getSongs().addAll(songRepository.findAllById(songIds));
		quizRepository.save(quiz);
		model.addAttribute("message", String.format("Added %d Songs to the Quiz %s:", songIds.size(), quiz.getTitle()));

		return "forward:editQuiz";
	}

	@RequestMapping("/removesongfromquiz")
	@Transactional
	public String deletesongfromquiz(Model model, @RequestParam int songId, @RequestParam int quizId) {

		List<SongModel> songs = songRepository.findByQuizzesId(quizId);
		QuizModel quiz = quizRepository.findById(quizId).get();
		SongModel song = songRepository.findById(songId).get();
		songs.remove(song);
		quiz.setSongs(songs);
		quizRepository.save(quiz);
		model.addAttribute("message", String.format("Removed song with ID %d from the Quiz:", songId));
		return "forward:editQuiz";
	}

	@RequestMapping(value = "/savequiz", method = RequestMethod.POST)
	@Transactional
	public String saveData(@RequestParam String quizname,
			@RequestParam(name = "songId", required = false) List<Integer> songIds, Model model) {
		DataFactory df = new DataFactory();
		Calendar publishDate = Calendar.getInstance();
		publishDate.setTime(new Date());
		QuizModel quizModel = new QuizModel(quizname, 1, publishDate);

		if (CollectionUtils.isEmpty(songIds)) {
			model.addAttribute("errorMessage", "No songs selected!");
			return "forward:quizAdmin";
		}
		if (quizname == null || quizname.isEmpty()) {
			model.addAttribute("warningMessage", "Please name the quiz");
			return "forward:quizAdmin";
		}

		quizModel.setSongs(songRepository.findAllById(songIds));
		quizRepository.save(quizModel);
		return "forward:quizManagement";
	}

	@RequestMapping(value = "/play")
	public String handlePlay(@RequestParam(value = "gid") String gids,
			@RequestParam(value = "nickname") String nickname, @RequestParam(value = "qid", required = false) int qid,
			Model model) {
		
		int gid;
		try {
			gid = Integer.parseInt(gids);
		} catch (NumberFormatException e) {
			model.addAttribute("errorMessage", "Only numbers allowed!");
			return "login";
		}
		
		model.addAttribute("gameIndex", gid);
		Optional<QuizModel> quizOpt = quizRepository.findById(gid);

		if (!quizOpt.isPresent() || nickname.isEmpty()) {
			model.addAttribute("errorMessage", "Game not found or empty nickname");
			return "login";
		} else {
			QuizModel quiz = quizOpt.get();
			List<SongModel> currSongs = new ArrayList<SongModel>(quiz.getSongs());
			if (qid < currSongs.size()) {
				SongModel currQuestion = currSongs.get(qid);
				model.addAttribute("currDocument", currQuestion.getDocument().getId());
				model.addAttribute("questionIndex", qid + 1);
				model.addAttribute("nickname", nickname);
				model.addAttribute("startTime", System.nanoTime());
				model.addAttribute("playbackStart", currQuestion.getStartTime());
				model.addAttribute("timer", currQuestion.getTimeToAnswer());
				List<String> possibleAnswers = new ArrayList<String>();
				possibleAnswers.add(currQuestion.getAnswer1());
				possibleAnswers.add(currQuestion.getAnswer2());
				possibleAnswers.add(currQuestion.getAnswer3());
				possibleAnswers.add(currQuestion.getTitle());
				Collections.shuffle(possibleAnswers);
				model.addAttribute("possibleAnswers", possibleAnswers);

			} else {
				model.addAttribute("message", "You finished!");
				return "forward:statistics";
			}
			return "game";
		}
	}

	@RequestMapping("/quizStatistics")
	public String showQStatistics(Model model) {
		return "quizmaster";
	}

	@RequestMapping("/quizAdmin")
	@Transactional
	public String showQuizAdmin(Model model) {
		List<SongModel> songs = songRepository.findAll();
		model.addAttribute("songs", songs);
		return "quiz";
	}

	@RequestMapping("/quizManagement")
	@Transactional
	public String showQuizzes(Model model) {
		List<QuizModel> quizzes = quizRepository.findAll();
		model.addAttribute("quizzes", quizzes);
		return "quizManagement";
	}

	@RequestMapping("/editQuiz")
	@Transactional
	public String editQuiz(@RequestParam int quizId, Model model) {
		List<SongModel> songs = songRepository.findByQuizzesId(quizId);
		List<SongModel> allsongs = songRepository.findAll();
		QuizModel quiz = quizRepository.findById(quizId).get();
		model.addAttribute("title", quiz.getTitle());
		model.addAttribute("songs", songs);
		model.addAttribute("allsongs", allsongs);
		model.addAttribute("quizId", quizId);
		return "editQuiz";
	}

	@RequestMapping(value = "qrcode/{id}", method = RequestMethod.GET)
	public void qrcode(@PathVariable("id") String id, HttpServletResponse response, HttpServletRequest request)
			throws Exception {
		response.setContentType("image/png");
		OutputStream outputStream = response.getOutputStream();
		String reqSrc = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
				+ "/?qid=";
		outputStream.write(ZXingHelper.getQRCodeImage(reqSrc + id, 400, 400));
		outputStream.flush();
		outputStream.close();
	}
}

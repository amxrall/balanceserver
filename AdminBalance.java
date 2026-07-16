package net.sf.l2j.gameserver.handler.admincommandhandlers;

import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.instancemanager.custom.BalanceLoad;
import net.sf.l2j.gameserver.instancemanager.custom.BalancerEdit;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class AdminBalance implements IAdminCommandHandler
{
	private static final String COMMAND_BALANCE = "admin_balance";
	private static final String COMMAND_EDIT = "admin_editBalance";
	private static final String COMMAND_ADD = "admin_addBalance";
	private static final String COMMAND_SUB = "admin_subBalance";
	private static final String COMMAND_RELOAD = "admin_reloadBalance";

	private static final String[] ADMIN_COMMANDS =
	{
		COMMAND_BALANCE,
		COMMAND_EDIT,
		COMMAND_ADD,
		COMMAND_SUB,
		COMMAND_RELOAD
	};

	@Override
	public boolean useAdminCommand(String command, L2PcInstance activeChar)
	{
		if (activeChar == null || command == null)
			return false;

		if (command.equals(COMMAND_BALANCE))
		{
			showMainWindow(activeChar);
			return true;
		}

		if (command.startsWith(COMMAND_EDIT))
		{
			showClassBalance(command, activeChar);
			return true;
		}

		if (command.startsWith(COMMAND_ADD))
		{
			editBalance(command, activeChar, true);
			return true;
		}

		if (command.startsWith(COMMAND_SUB))
		{
			editBalance(command, activeChar, false);
			return true;
		}

		if (command.equals(COMMAND_RELOAD))
		{
			BalanceLoad.load();

			activeChar.sendMessage("Balanceamento recarregado do banco de dados.");
			showMainWindow(activeChar);

			return true;
		}

		return false;
	}

	private static void showMainWindow(L2PcInstance activeChar)
	{
		final NpcHtmlMessage htm = new NpcHtmlMessage(0);
		htm.setFile("data/html/admin/balance/main.htm");

		activeChar.sendPacket(htm);
	}

	private static void showClassBalance(String command, L2PcInstance activeChar)
	{
		try
		{
			final String parameter = command.substring(COMMAND_EDIT.length()).trim();

			if (parameter.isEmpty())
			{
				activeChar.sendMessage("Informe o ID da classe.");
				return;
			}

			final int classId = Integer.parseInt(parameter);

			if (!BalanceLoad.isValidClassId(classId))
			{
				activeChar.sendMessage("ID de classe invalido: " + classId);
				return;
			}

			/*
			 * Garante que a linha exista mesmo antes da primeira edição.
			 */
			if (!BalanceLoad.ensureClassRow(classId))
			{
				activeChar.sendMessage("Nao foi possivel preparar o balanceamento da classe.");
				return;
			}

			BalancerEdit.sendBalanceWindow(classId, activeChar);
		}
		catch (NumberFormatException e)
		{
			activeChar.sendMessage("ID de classe invalido.");
		}
		catch (Exception e)
		{
			activeChar.sendMessage("Erro ao abrir o balanceamento da classe.");
			e.printStackTrace();
		}
	}

	private static void editBalance(String command, L2PcInstance activeChar, boolean add)
	{
		final String commandName = add ? COMMAND_ADD : COMMAND_SUB;

		try
		{
			final String parameters = command.substring(commandName.length()).trim();

			if (parameters.isEmpty())
			{
				activeChar.sendMessage(
					"Use: //" +
					commandName.substring(6) +
					" STAT CLASS_ID VALOR"
				);

				return;
			}

			final String[] args = parameters.split("\\s+");

			if (args.length < 3)
			{
				activeChar.sendMessage(
					"Use: //" +
					commandName.substring(6) +
					" STAT CLASS_ID VALOR"
				);

				return;
			}

			final String stat = args[0].toLowerCase();
			final int classId = Integer.parseInt(args[1]);
			final int value = Integer.parseInt(args[2]);

			if (!BalanceLoad.isValidClassId(classId))
			{
				activeChar.sendMessage("ID de classe invalido: " + classId);
				return;
			}

			if (value < 0)
			{
				activeChar.sendMessage("Informe um valor positivo.");
				return;
			}

			final boolean saved = BalancerEdit.editStat(
				stat,
				classId,
				value,
				add
			);

			if (!saved)
			{
				activeChar.sendMessage(
					"Erro ao salvar o balanceamento. Verifique o console."
				);

				BalancerEdit.sendBalanceWindow(classId, activeChar);
				return;
			}

			activeChar.sendMessage(
				"Balanceamento salvo: classe " +
				classId +
				", status " +
				stat +
				", novo valor " +
				BalanceLoad.getValue(stat, classId) +
				"."
			);

			BalancerEdit.sendBalanceWindow(classId, activeChar);
		}
		catch (NumberFormatException e)
		{
			activeChar.sendMessage("ID da classe ou valor invalido.");
		}
		catch (Exception e)
		{
			activeChar.sendMessage("Erro ao alterar o balanceamento.");
			e.printStackTrace();
		}
	}

	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}